package movieticketreservation.model;

public class Movie {
    private int id;
    private String slug;
    private String title;
    private int releaseYear;
    private String genres;
    private int runtimeMinutes;
    private String posterPath;
    private String synopsis;

    public Movie() {}

    public Movie(int id, String slug, String title, int releaseYear, String genres,
                 int runtimeMinutes, String posterPath, String synopsis) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.releaseYear = releaseYear;
        this.genres = genres;
        this.runtimeMinutes = runtimeMinutes;
        this.posterPath = posterPath;
        this.synopsis = synopsis;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(int releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public int getRuntimeMinutes() {
        return runtimeMinutes;
    }

    public void setRuntimeMinutes(int runtimeMinutes) {
        this.runtimeMinutes = runtimeMinutes;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    public String getRuntimeFormatted() {
        int hours = runtimeMinutes / 60;
        int minutes = runtimeMinutes % 60;
        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        }
        return minutes + "m";
    }
}
