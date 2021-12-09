package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class ReactionsMenuInfoView extends FrameLayout {
    private final FlickerLoadingView flickerLoadingView;
    private final TextView titleView;
    private final AvatarsImageView avatarsImageView;
    private final int currentAccount;
    private final ImageView iconView;
    private final MessageObject message;
    boolean ignoreLayout;
    public ArrayList<TLRPC.User> users = new ArrayList<>();
    public LongSparseArray<TLRPC.User> userReactions = new LongSparseArray<>();

    public ReactionsMenuInfoView(Context context, int currentAccount, MessageObject message) {
        super(context);
        this.currentAccount = currentAccount;
        this.message = message;

        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 0, 62, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

        iconView = new ImageView(context);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);

        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);

        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();

        long currentUserId = currentUser == null ? 0 : currentUser.id;
        MessagesController msgController = MessagesController.getInstance(currentAccount);

        ArrayList<TLRPC.InputUser> userIds = new ArrayList(3);

        if(message.messageOwner.reactions != null && message.messageOwner.reactions.recent_reactons != null) {
            for (TLRPC.TL_messageUserReaction item : message.messageOwner.reactions.recent_reactons) {
                if (item.user_id != currentUserId) {
                    TLRPC.User user = msgController.getUser(item.user_id);
                    if (user == null) {
                        userIds.add(MessagesController.getInstance(currentAccount).getInputUser(item.user_id));
                    } else {
                        this.users.add(user);
                    }

                }
            }
        }


        if(userIds.size() > 0) {
            TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
            req.id = userIds;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (res != null) {
                    TLRPC.Vector vector = (TLRPC.Vector) res;
                    for (Object o : vector.objects) {
                        this.users.add((TLRPC.User) o);
                    }
                } else if (error != null) {
                    FileLog.e(error.text);
                }
                updateView();
            }));
        }

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        setEnabled(false);

        updateView();
    }

    public ReactionsInfoPopupLayout createResults(ChatActivity chatActivity, ChatActivity.ThemeDelegate themeDelegate){
        if (message != null && message.messageOwner != null && message.messageOwner.reactions.results != null && message.messageOwner.reactions.results.size() <= 10) {

            int totalReactionsCount = 0;
            for (TLRPC.TL_reactionCount item : message.messageOwner.reactions.results) {
                totalReactionsCount += item.count;
            }
            return new ReactionsInfoPopupLayout( getContext(),chatActivity,message,themeDelegate,null, totalReactionsCount);
        }

        return new ReactionsInfoPopupLayout(getContext(),chatActivity,message,themeDelegate);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    private void updateView() {
        setEnabled(users.size() > 0);
        for (int i = 0; i < 3; i++) {
            if (i < users.size()) {
                avatarsImageView.setObject(i, currentAccount, users.get(i));
            } else {
                avatarsImageView.setObject(i, currentAccount, null);
            }
        }
        if (users.size() == 1) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
        } else if (users.size() == 2) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
        } else {
            avatarsImageView.setTranslationX(0);
        }

        int totalReactions = 0;
        if (message != null && message.messageOwner != null && message.messageOwner.reactions.results != null) {
            for (TLRPC.TL_reactionCount item : message.messageOwner.reactions.results) {
                totalReactions += item.count;
            }
        }

        avatarsImageView.commitTransition(false);
//        if (peerIds.size() == 1 && users.get(0) != null) {
//            titleView.setText(ContactsController.formatName(users.get(0).first_name, users.get(0).last_name));
//        } else {
        titleView.setText(LocaleController.formatPluralString("ReactionsTotal", totalReactions));
        //}

        titleView.animate().alpha(1f).setDuration(220).start();
        avatarsImageView.animate().alpha(1f).setDuration(220).start();
        flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (flickerLoadingView.getVisibility() == View.VISIBLE) {
            ignoreLayout = true;
            flickerLoadingView.setVisibility(View.GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
            ignoreLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

}
