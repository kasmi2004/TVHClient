package org.tvheadend.tvhclient.features.playback;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import org.tvheadend.tvhclient.data.entity.Channel;
import org.tvheadend.tvhclient.data.entity.ServerProfile;
import org.tvheadend.tvhclient.data.service.EpgSyncService;

import timber.log.Timber;

public class CastChannelActivity extends BasePlaybackActivity {

    private int channelId;
    private CastSession castSession;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            channelId = getIntent().getIntExtra("channelId", -1);
        } else {
            Bundle bundle = getIntent().getExtras();
            if (bundle != null) {
                channelId = bundle.getInt("channelId", -1);
            }
        }
        CastContext castContext = CastContext.getSharedInstance(this);
        castSession = castContext.getSessionManager().getCurrentCastSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (castSession == null) {
            progressBar.setVisibility(View.GONE);
            statusTextView.setText("No cast session available");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("channelId", channelId);
    }

    @Override
    protected void getHttpTicket() {
        Intent intent = new Intent(this, EpgSyncService.class);
        intent.setAction("getTicket");
        intent.putExtra("channelId", channelId);
        startService(intent);
    }

    @Override
    protected void onHttpTicketReceived(String path, String ticket) {

        Channel channel = appRepository.getChannelData().getItemById(channelId);
        String baseUrl = connection.getHostname() + ":" + connection.getStreamingPort() + serverStatus.getWebroot();
        String iconUrl = baseUrl + "/" + channel.getIcon();

        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        movieMetadata.putString(MediaMetadata.KEY_TITLE, channel.getProgramTitle());
        movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, channel.getProgramSubtitle());
        movieMetadata.addImage(new WebImage(Uri.parse(iconUrl)));   // small cast icon
        movieMetadata.addImage(new WebImage(Uri.parse(iconUrl)));   // large background icon

        String url = baseUrl + "/stream/channelnumber/" + channel.getNumber();
        ServerProfile serverProfile = appRepository.getServerProfileData().getItemById(serverStatus.getCastingServerProfileId());
        if (serverProfile != null) {
            url += "?profile=" + serverProfile.getName();
        }
        Timber.d("Trying to cast channel with url: " + url);

        MediaInfo mediaInfo = new MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType("video/webm")
                .setMetadata(movieMetadata)
                .setStreamDuration(0)
                .build();

        RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        remoteMediaClient.load(mediaInfo, true, 0);
        finish();
    }
}