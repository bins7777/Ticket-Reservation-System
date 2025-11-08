package movieticketreservation.dao;

import movieticketreservation.db.Database;
import movieticketreservation.model.Movie;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MovieDAO {

    private Movie mapRow(ResultSet rs) throws SQLException {
        return new Movie(
                rs.getInt("id"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getInt("release_year"),
                rs.getString("genres"),
                rs.getInt("runtime_minutes"),
                rs.getString("poster_path"),
                rs.getString("synopsis")
        );
    }

    public List<Movie> findAll() throws SQLException {
        String sql = "SELECT id, slug, title, release_year, genres, runtime_minutes, poster_path, synopsis " +
                     "FROM movies ORDER BY title";
        List<Movie> movies = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                movies.add(mapRow(rs));
            }
        }
        return movies;
    }

    public Optional<Movie> findById(int id) throws SQLException {
        String sql = "SELECT id, slug, title, release_year, genres, runtime_minutes, poster_path, synopsis " +
                     "FROM movies WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Movie> findBySlug(String slug) throws SQLException {
        String sql = "SELECT id, slug, title, release_year, genres, runtime_minutes, poster_path, synopsis " +
                     "FROM movies WHERE slug = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Movie createMovie(String slug,
                             String title,
                             int releaseYear,
                             String genres,
                             int runtimeMinutes,
                             String posterPath,
                             String synopsis) throws SQLException {
        String sql = "INSERT INTO movies(slug, title, release_year, genres, runtime_minutes, poster_path, synopsis) " +
                     "VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, slug);
            ps.setString(2, title);
            ps.setInt(3, releaseYear);
            ps.setString(4, genres);
            ps.setInt(5, runtimeMinutes);
            ps.setString(6, posterPath);
            ps.setString(7, synopsis);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    return new Movie(id, slug, title, releaseYear, genres, runtimeMinutes, posterPath, synopsis);
                }
            }
        }
        throw new SQLException("Failed to create movie record for slug: " + slug);
    }

    public boolean deleteMovie(int movieId) throws SQLException {
        String sql = "DELETE FROM movies WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            return ps.executeUpdate() > 0;
        }
    }
}
