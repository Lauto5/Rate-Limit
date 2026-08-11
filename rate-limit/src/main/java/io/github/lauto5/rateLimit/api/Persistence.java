package io.github.lauto5.rateLimit.api;

import io.github.lauto5.rateLimit.infraestructure.InMemoryStore;

public class Persistence {

	public static InMemoryStore inMemory() {
		return new InMemoryStore();
	}
	
}
