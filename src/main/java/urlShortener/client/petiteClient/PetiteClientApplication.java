package urlShortener.client.petiteClient;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PetiteClientApplication implements CommandLineRunner {

	private final ShortUrlSimulator simulator;
	public PetiteClientApplication(ShortUrlSimulator simulator) {
		this.simulator = simulator;
	}

	public static void main(String[] args) {
		SpringApplication.run(PetiteClientApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		simulator.run();
	}
}
