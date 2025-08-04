package urlShortener.client.petiteClient.dto;

public class ShortenResponse {
    private String shortUrl;
    private String status;
    private String error;

    public ShortenResponse(String shortUrl, String status, String error) {
        this.shortUrl = shortUrl;
        this.status = status;
        this.error = error;
    }

    // Getters
    public String getShortUrl() { return shortUrl; }
    public String getStatus() { return status; }
    public String getError() { return error; }
}
