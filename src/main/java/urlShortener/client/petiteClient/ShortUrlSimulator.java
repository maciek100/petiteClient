package urlShortener.client.petiteClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import urlShortener.client.petiteClient.dto.CacheStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class ShortUrlSimulator {

    private final WebClient webClient = WebClient.create("http://petiteurl-service:8080");
    private final List<String> shortCodes = new ArrayList<>();
    @Value("${petite.client.url.store.file.name}")
    private String fileName;
    private static final Logger logger = LoggerFactory.getLogger(ShortUrlSimulator.class.getName());

    public ShortUrlSimulator () {
        System.out.println("SHORTIE " + this.hashCode());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    private List<String> suckInURLs (String fileName) {
        List<String> urls;
        try {
            logger.info("Original Storage File is = {}", fileName);
            urls = Files.readAllLines(Paths.get(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            logger.info("Waiting 10 seconds before tickling cache. Making sure Mongo is happy and ready.");
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return urls;
    }

    public void run() {
        try {
            // a trick to wait until the service is ready ...
            String retryResult = callWithRetry();
            logger.info("Result of waiting for service is :{}", retryResult);
        } catch (InterruptedException | RuntimeException e) {
            logger.error("Failed at waiting for the service ...{}", e.getMessage(), e);
           ///// throw new RuntimeException(e);
        }
        logger.info("Cache is {}", isCacheWarm() ? "warm" : "cold");
        try {
            List<String> urls = suckInURLs(fileName);

            for (String url : urls) {
                logger.info("Current ulr is = {}", url);
                if(url == null || url.isBlank()) {
                    logger.info("EMPTY UR: {}||", url);
                    continue;
                }
                Map<String, String> body = Map.of("url", url);
                ResponseEntity<Map<String, Object>> response = webClient.post()
                        .uri("/urlShort/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .block();
                if (response != null && response.getStatusCode().is2xxSuccessful()) {
                    String code = (String) response.getBody().get("shortUrl");
                    shortCodes.add(code);
                    logger.info("Shortened: {} -> {}", url, code);
                }
            }
            logger.info("Done pre-populating data store.");
            queryTheService();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public String callWithRetry() throws InterruptedException {
        int maxRetries = 10;
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                String result = webClient.get()
                        .uri("/urlShort/api/v1/alive")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                logger.info("Server is alive and ready : {}", result);
                return result;
            } catch (Exception e) {
                attempt++;
                logger.info("Service not ready, retrying in 3 seconds... attempt {}", attempt);
                Thread.sleep(3000);
            }
        }
        throw new RuntimeException("Service unavailable after " + maxRetries + " retries");
    }
    //This method is supposed just to trigger action on the server, and it does not expect any response.
    //If the failure happened on the server we will just log in the status code ... not perfect ...yet
    public void queryTheService () {
        Random rand = new Random();
        for (int i = 0; i < 3000; i++) {
            String code = shortCodes.get(rand.nextInt(shortCodes.size()));
            logger.info("Obtained random code = {}", code);
            try {
                ResponseEntity<String> responseEntity = webClient.get()
                        .uri("/urlShort/api/v1/{id}", code)
                        .retrieve()
                        .toEntity(String.class)
                        .block();
                logger.info("Hit: {} -> {}", code, responseEntity.getStatusCode());
            } catch (WebClientResponseException e) {
                // Handles HTTP errors (404, 410, etc.)
                logger.warn("Expired/invalid hit: {} -> {}", code, e.getStatusCode());
            } catch (Exception e) {
                // Handles connection issues, timeouts, etc.
                logger.error("Unexpected error for code {}: {}", code, e.getMessage());
            }
        }
        logger.info("Done exercising ...");
    }

    public boolean isCacheWarm () {
        try {
            CacheStats stats = webClient.get()
                    .uri("/urlShort/api/v1/cache_size")
                    .retrieve()
                    .bodyToMono(CacheStats.class)
                    .block();


                logger.info("Retrieved Cache size is: {}", stats == null ? 0 : stats.size());
                return (stats != null  && stats.size() > 50);
        } catch (Exception e) {
            logger.error("EXCEPTION while retrieving cache size: {}", e.getMessage());
        }
        return false;
    }
}
