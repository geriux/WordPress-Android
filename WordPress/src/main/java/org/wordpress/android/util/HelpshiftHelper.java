package org.wordpress.android.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.helpshift.Core;
import com.helpshift.InstallConfig;
import com.helpshift.exceptions.InstallException;
import com.helpshift.support.Support;
import com.helpshift.support.Support.Delegate;

import org.apache.commons.lang.ArrayUtils;
import org.wordpress.android.BuildConfig;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.analytics.AnalyticsTracker.Stat;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.store.SiteStore;
import org.wordpress.android.util.AppLog.T;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class HelpshiftHelper {
    public static String ORIGIN_KEY = "ORIGIN_KEY";
    private static String HELPSHIFT_SCREEN_KEY = "helpshift_screen";
    private static String HELPSHIFT_ORIGIN_KEY = "origin";
    private static HelpshiftHelper mInstance = null;
    private static HashMap<String, Object> mMetadata = new HashMap<String, Object>();

    public enum MetadataKey {
        USER_ENTERED_URL("user-entered-url"),
        USER_ENTERED_USERNAME("user-entered-username");

        private final String mStringValue;

        private MetadataKey(final String stringValue) {
            mStringValue = stringValue;
        }

        public String toString() {
            return mStringValue;
        }
    }

    public enum Tag {
        ORIGIN_UNKNOWN("origin:unknown"),
        ORIGIN_LOGIN_SCREEN_HELP("origin:login-screen-help"),
        ORIGIN_LOGIN_SCREEN_ERROR("origin:login-screen-error"),
        ORIGIN_ME_SCREEN_HELP("origin:me-screen-help"),
        ORIGIN_START_OVER("origin:start-over"),
        ORIGIN_DELETE_SITE("origin:delete-site");

        private final String mStringValue;

        private Tag(final String stringValue) {
            mStringValue = stringValue;
        }

        public String toString() {
            return mStringValue;
        }

        public static String[] toString(Tag[] tags) {
            if (tags == null) {
                return null;
            }
            String[] res = new String[tags.length];
            for (int i = 0; i < res.length; i++) {
                res[0] = tags[0].toString();
            }
            return res;
        }
    }

    private HelpshiftHelper() {
    }

    public static synchronized HelpshiftHelper getInstance() {
        if (mInstance == null) {
            mInstance = new HelpshiftHelper();
        }
        return mInstance;
    }

    public static void init(Application application) {
        InstallConfig installConfig = new InstallConfig.Builder()
                .setEnableInAppNotification(true)
                .build();
        Core.init(Support.getInstance());
        try {
            Core.install(application, BuildConfig.HELPSHIFT_API_KEY, BuildConfig.HELPSHIFT_API_DOMAIN,
                    BuildConfig.HELPSHIFT_API_ID, installConfig);
        } catch (InstallException e) {
            AppLog.e(T.UTILS, e);
        }
        Support.setDelegate(new Delegate() {
            @Override
            public void sessionBegan() {
            }

            @Override
            public void sessionEnded() {
            }

            @Override
            public void newConversationStarted(String s) {
            }

            @Override
            public void userRepliedToConversation(String s) {
                AnalyticsTracker.track(Stat.SUPPORT_SENT_REPLY_TO_SUPPORT_MESSAGE);
            }

            @Override
            public void userCompletedCustomerSatisfactionSurvey(int i, String s) {
            }

            @Override
            public void displayAttachmentFile(File file) {
            }

            @Override
            public void didReceiveNotification(int i) {
            }
        });
    }

    /**
     * Show conversation activity
     * Automatically add default metadata to this conversation
     */
    public void showConversation(Activity activity, SiteStore siteStore, Tag origin, String wpComUsername) {
        if (origin == null) {
            origin = Tag.ORIGIN_UNKNOWN;
        }
        // track origin and helpshift screen in analytics
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(HELPSHIFT_SCREEN_KEY, "conversation");
        properties.put(HELPSHIFT_ORIGIN_KEY, origin.toString());
        AnalyticsTracker.track(Stat.SUPPORT_OPENED_HELPSHIFT_SCREEN, properties);
        // Add tags to Helpshift metadata
        addTags(new Tag[]{origin});
        HashMap config = getHelpshiftConfig(activity, siteStore, wpComUsername);
        Support.showConversation(activity, config);
    }

    /**
     * Show FAQ activity
     * Automatically add default metadata to this conversation (users can start a conversation from FAQ screen).
     */
    public void showFAQ(Activity activity, SiteStore siteStore, Tag origin, String wpComUsername) {
        if (origin == null) {
            origin = Tag.ORIGIN_UNKNOWN;
        }
        // track origin and helpshift screen in analytics
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(HELPSHIFT_SCREEN_KEY, "faq");
        properties.put(HELPSHIFT_ORIGIN_KEY, origin.toString());
        AnalyticsTracker.track(Stat.SUPPORT_OPENED_HELPSHIFT_SCREEN, properties);
        // Add tags to Helpshift metadata
        addTags(new Tag[]{origin});
        HashMap config = getHelpshiftConfig(activity, siteStore, wpComUsername);
        Support.showFAQs(activity, config);
    }

    /**
     * Register a GCM device token to Helpshift servers
     *
     * @param regId registration id
     */
    public void registerDeviceToken(Context context, String regId) {
        if (!TextUtils.isEmpty(regId)) {
            Core.registerDeviceToken(context, regId);
        }
    }

    public void setTags(Tag[] tags) {
        mMetadata.put(Support.TagsKey, Tag.toString(tags));
    }

    public void addTags(Tag[] tags) {
        String[] oldTags = (String[]) mMetadata.get(Support.TagsKey);
        // Concatenate arrays
        mMetadata.put(Support.TagsKey, ArrayUtils.addAll(oldTags, Tag.toString(tags)));
    }

    /**
     * Handle push notification
     */
    public void handlePush(Context context, Intent intent) {
        Core.handlePush(context, intent);
    }

    /**
     * Add metadata to Helpshift conversations
     *
     * @param key map key
     * @param object to store. Be careful with the type used. Nothing is specified in the documentation. Better to use
     *               String but String[] is needed for specific key like Support.TagsKey
     */
    public void addMetaData(MetadataKey key, Object object) {
        mMetadata.put(key.toString(), object);
    }

    public Object getMetaData(MetadataKey key) {
        return mMetadata.get(key.toString());
    }

    private void addDefaultMetaData(Context context, SiteStore siteStore, String wpComUsername) {
        // Use plain text log (unfortunately Helpshift can't display this correctly)
        mMetadata.put("log", AppLog.toPlainText(context));

        // List blogs name and url
        int counter = 1;
        for (SiteModel site: siteStore.getSites()) {
            mMetadata.put("blog-name-" + counter, site.getName());
            mMetadata.put("blog-url-" + counter, site.getUrl());
            counter += 1;
        }

        // wpcom user
        mMetadata.put("wpcom-username", wpComUsername);
    }

    private HashMap getHelpshiftConfig(Context context, SiteStore siteStore, String wpComUsername) {
        String emailAddress = UserEmailUtils.getPrimaryEmail(context);
        // Use the user entered username to pre-fill name
        String name = (String) getMetaData(MetadataKey.USER_ENTERED_USERNAME);
        // If it's null or empty, use split email address to pre-fill name
        if (TextUtils.isEmpty(name)) {
            String[] splitEmail = TextUtils.split(emailAddress, "@");
            if (splitEmail.length >= 1) {
                name = splitEmail[0];
            }
        }
        Core.setNameAndEmail(name, emailAddress);
        addDefaultMetaData(context, siteStore, wpComUsername);
        HashMap config = new HashMap ();
        config.put(Support.CustomMetadataKey, mMetadata);
        return config;
    }
}
