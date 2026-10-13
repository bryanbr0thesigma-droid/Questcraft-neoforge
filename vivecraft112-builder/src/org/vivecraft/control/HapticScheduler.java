package org.vivecraft.control;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.vivecraft.provider.MCOpenVR;

public class HapticScheduler {
	private ScheduledExecutorService executor;

	public HapticScheduler() {
		executor = Executors.newSingleThreadScheduledExecutor();
	}

	private void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude) {
		MCOpenVR.applyHapticFeedback(controller, durationSeconds, frequency, amplitude);
	}

	public void queueHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude, float delaySeconds) {
		executor.schedule(() -> triggerHapticPulse(controller, durationSeconds, frequency, amplitude), (long)(delaySeconds * 1000000), TimeUnit.MICROSECONDS);
	}
}
