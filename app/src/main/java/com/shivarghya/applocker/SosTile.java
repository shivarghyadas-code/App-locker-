package com.shivarghya.applocker;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** "SOS" tile in the notification panel: one tap sends the location to the emergency numbers. */
public class SosTile extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Alert.numbers(this).isEmpty() ? Tile.STATE_UNAVAILABLE : Tile.STATE_INACTIVE);
        t.setLabel("SOS");
        t.updateTile();
    }

    @Override
    public void onClick() {
        if (Alert.numbers(this).isEmpty() || !Alert.canSend(this)) {
            openScreen(false);           // shows what is missing
            return;
        }
        // Works even from the lock screen: sends straight away, no need to unlock the phone.
        Alert.sos(this, null);
        Toast.makeText(this, "SOS: sending your location…", Toast.LENGTH_LONG).show();
        Tile t = getQsTile();
        if (t != null) { t.setState(Tile.STATE_ACTIVE); t.updateTile(); }
    }

    private void openScreen(boolean sendNow) {
        Intent i = new Intent(this, SosActivity.class)
                .putExtra(SosActivity.EXTRA_SEND_NOW, sendNow)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
