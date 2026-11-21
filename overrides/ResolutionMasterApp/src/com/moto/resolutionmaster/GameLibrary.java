package com.moto.resolutionmaster;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Real, launchable, locally installed games; no fabricated tiles. */
public final class GameLibrary {
    private GameLibrary() {}

    public static final class Game {
        public final String packageName;
        public final String label;

        Game(String pkg, String name) {
            packageName = pkg;
            label = name;
        }
    }

    public static List<Game> installedGames(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> available = pm.queryIntentActivities(launcher, 0);
        List<Game> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (available == null) return result;
        for (ResolveInfo ri : available) {
            if (ri == null || ri.activityInfo == null ||
                    ri.activityInfo.applicationInfo == null) continue;
            ApplicationInfo app = ri.activityInfo.applicationInfo;
            String pkg = ri.activityInfo.packageName;
            if (!GameLibraryPolicy.include(
                    pkg, app.category, ApplicationInfo.CATEGORY_GAME) ||
                    !seen.add(pkg)) continue;
            CharSequence title = app.loadLabel(pm);
            String name = title == null || title.toString().trim().isEmpty()
                ? pkg : title.toString().trim();
            result.add(new Game(pkg, name));
        }
        Collections.sort(result, new Comparator<Game>() {
            @Override public int compare(Game a, Game b) {
                return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
            }
        });
        return result;
    }
}
