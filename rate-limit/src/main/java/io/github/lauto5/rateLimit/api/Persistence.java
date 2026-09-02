package io.github.lauto5.rateLimit.api;

import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.infraestructure.InMemoryStore;
import io.github.lauto5.rateLimit.infraestructure.LettuceKeyValueStore;
import io.github.lauto5.rateLimit.infraestructure.RedisStore;

public class Persistence {

	public static RateLimitStore inMemory() {
		return new InMemoryStore();
	}
	
	public static RateLimitStore inRedis(String url) {
		return new RedisStore(new LettuceKeyValueStore(url));
	}
	
}
