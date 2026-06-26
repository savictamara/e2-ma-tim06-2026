package rs.ac.uns.ftn.slagalica.util;

import android.app.Activity;
import android.content.Intent;

import rs.ac.uns.ftn.slagalica.AsocijacijeActivity;
import rs.ac.uns.ftn.slagalica.FinalResultActivity;
import rs.ac.uns.ftn.slagalica.KoZnaZnaActivity;
import rs.ac.uns.ftn.slagalica.KorakPoKorakActivity;
import rs.ac.uns.ftn.slagalica.MojBrojActivity;
import rs.ac.uns.ftn.slagalica.SkockoActivity;
import rs.ac.uns.ftn.slagalica.SpojniceActivity;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;

public final class GameFlow {
    public static final String EXTRA_GAME_ID = "rs.ac.uns.ftn.slagalica.EXTRA_GAME_ID";
    public static final String EXTRA_FULL_MATCH = "rs.ac.uns.ftn.slagalica.EXTRA_FULL_MATCH";

    private GameFlow() {
    }

    public static boolean isFullMatch(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_FULL_MATCH, false);
    }

    public static String existingGameId(Intent intent) {
        return intent == null ? "" : value(intent.getStringExtra(EXTRA_GAME_ID));
    }

    public static boolean hasExistingGame(Intent intent) {
        return !existingGameId(intent).isEmpty();
    }

    public static boolean openMiniGame(Activity activity, String gameId, String miniGame) {
        Class<?> activityClass = activityClassFor(miniGame);
        if (activityClass == null || activity == null || gameId == null || gameId.isEmpty()) {
            return false;
        }
        Intent intent = new Intent(activity, activityClass);
        intent.putExtra(EXTRA_GAME_ID, gameId);
        intent.putExtra(EXTRA_FULL_MATCH, true);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }

    public static void openFinalResult(Activity activity, String gameId) {
        if (activity == null || gameId == null || gameId.isEmpty()) {
            return;
        }
        Intent intent = new Intent(activity, FinalResultActivity.class);
        intent.putExtra(EXTRA_GAME_ID, gameId);
        intent.putExtra(EXTRA_FULL_MATCH, true);
        activity.startActivity(intent);
        activity.finish();
    }

    public static Class<?> activityClassFor(String miniGame) {
        if (GameRepository.MINI_KNOW_IT.equals(miniGame)) {
            return KoZnaZnaActivity.class;
        }
        if (GameRepository.MINI_CONNECTIONS.equals(miniGame)) {
            return SpojniceActivity.class;
        }
        if (GameRepository.MINI_ASSOCIATIONS.equals(miniGame)) {
            return AsocijacijeActivity.class;
        }
        if (GameRepository.MINI_SKOCKO.equals(miniGame)) {
            return SkockoActivity.class;
        }
        if (GameRepository.MINI_STEP_BY_STEP.equals(miniGame)) {
            return KorakPoKorakActivity.class;
        }
        if (GameRepository.MINI_MY_NUMBER.equals(miniGame)) {
            return MojBrojActivity.class;
        }
        return null;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
