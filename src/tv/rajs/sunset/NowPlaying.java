package tv.rajs.sunset;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so Android lets Sunset Home see which app is playing music (MediaSessionManager
 * requires an enabled notification listener). It does not read or keep any notification.
 */
public final class NowPlaying extends NotificationListenerService {
}
