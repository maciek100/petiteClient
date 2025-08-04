package urlShortener.client.petiteClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class ShortUrlSimulator {

    private final WebClient webClient = WebClient.create("http://petiteurl-service:8080");
    private final List<String> shortCodes = new ArrayList<>();
    @Value("${petite.client.url.store.file.name}")
    private String fileName;
    private static final Logger logger = Logger.getLogger(ShortUrlSimulator.class.getName());

    private List<String> suckInURLs (String fileName) {
        List<String> urls = null;
        try {
            System.out.println("FILE IS = " + fileName);
            urls = Files.readAllLines(Paths.get(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return urls;
    }

    public void run() {
        try {
            // a trick to wait until the service is ready ...
            String retryResult = callWithRetry();
            logger.info("Result of waiting for service is :" + retryResult);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE,"Failed at waiting for the service ..." + e.getMessage(), e);
            throw new RuntimeException(e);
        }
        logger.info("Cache is " + (isCacheWarm() ? "warm" : "cold"));
        try {
            List<String> urls = suckInURLs(fileName);

            for (String url : urls) {
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
                    logger.info("Shortened: " + url + " -> " + code);
                }
                Thread.sleep(100);
            }
            queryTheService();
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
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
                logger.info("Server is alive and ready : " + result);
                return result;
            } catch (Exception e) {
                attempt++;
                logger.info("Service not ready, retrying in 3 seconds... attempt " + attempt);
                Thread.sleep(3000);
            }
        }
        throw new RuntimeException("Service unavailable after " + maxRetries + "retries");
    }
    //This method is supposed just to trigger action on the server, and it does not expect any response.
    //If the failure happened on the server we will just log in the status code ... not perfect ...yet
    //TODO: perfect it ...
    @Scheduled(cron = "0 0/30 * * * ?")
    public void queryTheService () {
        Random rand = new Random();
        for (int i = 0; i < 500; i++) {
            String code = shortCodes.get(rand.nextInt(shortCodes.size()));
            logger.info("Obtained code = " + code);
            ResponseEntity<String> responseEntity = webClient.get()
                    .uri("/urlShort/api/v1/{id}", code)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
            logger.info("Hit: " + code + " -> " + responseEntity.getStatusCode());
        }
    }

    public boolean isCacheWarm () {
        try {
            Integer cacheSize = webClient.get()
                    .uri("/urlShort/api/v1/cache_size")
                    .retrieve()
                    .bodyToMono(Integer.class)
                    .block(); // Still blocking here if you’re not in a reactive app

            logger.info("Retrieved Cache size: " + cacheSize);
            return (cacheSize != null && cacheSize > 50);
        } catch (Exception e) {
            logger.log(Level.SEVERE,"EXCEPTION while retrieving cache size: " + e.getMessage());
        }
        return false;
    }
}
