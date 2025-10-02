package cz.mamstylcendy.cards.scraper;

import java.util.Random;

public class RandomWaiting {

    private final Random random = new Random();

    private final int minDelay;
    private final int maxDelay;

    public RandomWaiting(int minDelay, int maxDelay) {
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
    }

    public void sleep() throws InterruptedException {
        Thread.sleep(minDelay + random.nextInt(maxDelay - minDelay));
    }
}
