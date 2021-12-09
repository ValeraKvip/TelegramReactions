package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.ReactionsController;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

public class ReactionsListBubble extends FrameLayout {
    public static final int reactionSize = 30;
    public static final int reactionVerticalMargin = 7;
    public static final int reactionHorizontalMargin = 5;
    public static final int smallCircleSize = 5;
    private final ChatMessageCell messageCell;

    private LinearLayout reactionsContainer;
    private final ChatActivity chatActivity;
    private final MessageObject msg;
    private final Context context;
    private final Runnable dismissCallback;
    ArrayList<TLRPC.TL_availableReaction> reactions = new ArrayList<>(11);
    private final int currentAccount;

    private final ArrayList<RLottieDrawable> shakeAnimations = new ArrayList<>(11);
    private Timer shakeTimer;
    HorizontalScrollView scrollView;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private View circle;
    private View circle2;
    private AnimatorSet shakeAnimation;
    private boolean animated;
    private boolean alreadySelected;


    public ReactionsListBubble(@NonNull Context context, ChatActivity chatActivity, ChatMessageCell cell, MessageObject msg, Runnable dismiss) {
        super(context);
        this.context = context;
        this.msg = msg;
        this.messageCell = cell;
        this.chatActivity = chatActivity;
        this.currentAccount = chatActivity.getCurrentAccount();
        this.dismissCallback = dismiss;


        final Path clipPath = new Path();
        final RectF rect = new RectF();
        final Rect bounds = new Rect();

        scrollView = new HorizontalScrollView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                clipPath.reset();
                canvas.getClipBounds(bounds);
                rect.set(bounds);
                clipPath.addRoundRect(rect, 100, 100, Path.Direction.CW);
                clipPath.close();
                canvas.clipPath(clipPath);
                super.onDraw(canvas);
            }
        };
        // this.setMinimumHeight(AndroidUtilities.dp(60));

        Point cellSize = getCellSize();
        scrollView.setMinimumWidth(AndroidUtilities.dp(84));
        scrollView.setMinimumHeight(AndroidUtilities.dp(cellSize.y));

        Drawable shadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.reaction_bubble).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        scrollView.setBackground(shadowDrawable);
        scrollView.setClipChildren(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Rect scrollBounds = new Rect();
            scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (reactionsContainer == null) {
                    return;
                }
                scrollView.getGlobalVisibleRect(scrollBounds);
                float viewWidth = AndroidUtilities.dp(cellSize.x);
                for (int i = 0; i < reactionsContainer.getChildCount(); ++i) {
                    View view = reactionsContainer.getChildAt(i);


                    if (view.getGlobalVisibleRect(scrollBounds)
                            && view.getHeight() == scrollBounds.height()
                            && view.getWidth() == scrollBounds.width()) {
                        view.setAlpha(1);
                    } else {
                        float alpha;
                        float a = Math.abs(view.getWidth() - scrollBounds.width());
                        if (a <= 0) {
                            alpha = 0;
                        } else if (a >= viewWidth) {
                            alpha = 1;
                        } else {
                            alpha = 1 - (a / (viewWidth / 100f)) / 100f;
                            if (alpha < 0) {
                                alpha = 0;
                            }
                        }

                        view.setAlpha(alpha);
                    }
                }
            });
        }

        reactionsContainer = new LinearLayout(context);
        reactionsContainer.setOrientation(LinearLayout.HORIZONTAL);

        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setHorizontalScrollBarEnabled(false);

        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener(){
                    @Override
                    public void onGlobalLayout() {
                        startAppearAnimation();
                        scrollView.getViewTreeObserver().removeOnGlobalLayoutListener( this );
                    }
                });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (popupLayout != null) {

            widthMeasureSpec = MeasureSpec.makeMeasureSpec(popupLayout.getWidth() + AndroidUtilities.dp(getMenuMargin()-8), MeasureSpec.AT_MOST);
            Log.d("VAF","SIZE " + widthMeasureSpec);
        }
       // super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(260)  + AndroidUtilities.dp(getMenuMargin()-8), MeasureSpec.AT_MOST), heightMeasureSpec);


        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public static Point getCellSize() {
        return new Point(reactionSize + reactionHorizontalMargin*2,reactionSize + 2 * reactionVerticalMargin);
    }

    public int getMenuMargin() {
        return 36;//44-8
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public void setReactions(ArrayList<TLRPC.TL_availableReaction> pReactions) {
        if(circle != null){
            this.removeAllViews();
            scrollView.removeAllViews();
            reactionsContainer.removeAllViews();
        }

        this.reactions.clear();
        if (chatActivity != null) {
            TLRPC.Chat currentChat = chatActivity.getCurrentChat();

            if (currentChat != null && (currentChat.megagroup || ChatObject.isChannel(currentChat))) {
                TLRPC.ChatFull info = chatActivity.getCurrentChatInfo();
                if (info == null) {
                    info = chatActivity.getMessagesStorage().loadChatInfo(currentChat.id, ChatObject.isChannel(currentChat), new CountDownLatch(1), false, false);
                    if (info == null) {
                        return;
                    }
                }
                ArrayList<TLRPC.TL_availableReaction> availableReactions = new ArrayList<>(info.available_reactions.size());
                if (info.available_reactions.size() == pReactions.size()) {
                    availableReactions.addAll(pReactions);
                } else {
                    for (int i = 0; i < pReactions.size(); ++i) {
                        for (int j = 0; j < info.available_reactions.size(); ++j) {
                            if (pReactions.get(i).reaction.equals(info.available_reactions.get(j))) {
                                availableReactions.add(pReactions.get(i));
                                break;
                            }
                        }
                    }
                }
                this.reactions.addAll(availableReactions);
            } else {
                this.reactions.addAll(pReactions);
            }

        }

        for (TLRPC.TL_availableReaction reaction : this.reactions) {
            BackupImageView imageView = new BackupImageView(context);
            imageView.getImageReceiver().setAllowStartLottieAnimation(false);
            TLRPC.Document doc = reaction.select_animation;
            SvgHelper.SvgDrawable svgThumb;
            svgThumb = DocumentObject.getSvgThumb(doc, Theme.key_windowBackgroundGray, 1f);
            imageView.setImage(ImageLocation.getForDocument(doc), null, "tgs", svgThumb, reaction);
            imageView.getImageReceiver().setDelegate(new ImageReceiver.ImageReceiverDelegate() {
                @Override
                public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                }

                @Override
                public void onAnimationReady(ImageReceiver imageReceiver) {
                    RLottieDrawable anim = imageView.getImageReceiver().getLottieAnimation();
                    if (anim != null) {
                        shakeAnimations.add(anim);

                        if (shakeAnimations.size() == reactions.size()) {
                            startShakingAnimations();
                        }
                    }
                }
            });

            imageView.setOnClickListener(v -> {
                if (alreadySelected || !imageView.getImageReceiver().hasImageSet()) {
                    return;
                }

                alreadySelected = true;

                ReactionsController.getInstance(currentAccount).sendReaction(reaction.reaction, msg.getId(),
                        MessagesController.getInstance(currentAccount).getInputPeer(msg.messageOwner.peer_id), null, success -> AndroidUtilities.runOnUIThread(() -> {
                            if (success) {
                                if (dismissCallback != null) {
                                    dismissCallback.run();
                                }
                                if(messageCell != null){
                                    messageCell.setAwaitForReaction(reaction.reaction);
                                }

                                FloatingReaction floatingReaction = new FloatingReaction(context, reaction, messageCell,
                                        AndroidUtilities.getRealLocationOnScreen(context,imageView), chatActivity,imageView.getImageReceiver().getDrawable());
                                chatActivity.setFloatingReaction(floatingReaction);

                                RLottieDrawable anim = imageView.getImageReceiver().getLottieAnimation();
                                if (anim != null) {
                                    anim.stop();
                                    anim.setProgress(0);
                                }

                            } else {
                                if(messageCell != null){
                                    messageCell.setAwaitForReaction(null);
                                }

                                startChooseErrorAnimation(v);
                            }
                        }));
            });

            reactionsContainer.addView(imageView, LayoutHelper.createLinear(reactionSize, reactionSize,
                    Gravity.CENTER, reactionHorizontalMargin, reactionVerticalMargin, reactionHorizontalMargin, reactionVerticalMargin));
        }

        Drawable shadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.reaction_bubble).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));


        circle = new ImageView(context);
        circle.setBackground(shadowDrawable);

        circle2 = new ImageView(context);
        circle2.setBackground(shadowDrawable);

        if (this.reactions.size() == 1) {
//            if (popupLayout != null) {
//                popupLayout.setTranslationX(AndroidUtilities.dp(11));
//            }

            addView(circle, LayoutHelper.createFrame(12, 12, Gravity.RIGHT, 0, 38, 32, 0));
            addView(circle2, LayoutHelper.createFrame(smallCircleSize, smallCircleSize, Gravity.RIGHT, 0, 54, 31, 0));
            scrollView.addView(reactionsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        } else {
            addView(circle, LayoutHelper.createFrame(12, 12, Gravity.RIGHT, 0, 38, 30, 0));
            addView(circle2, LayoutHelper.createFrame(smallCircleSize, smallCircleSize, Gravity.RIGHT, 0, 54, 29, 0));
            scrollView.addView(reactionsContainer, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0));

        }

        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
       // startAppearAnimation();
    }

    public void startChooseErrorAnimation(View v) {
        Keyframe kf0 = Keyframe.ofFloat(0f, 0);
        Keyframe kf1 = Keyframe.ofFloat(0.2f, 20);
        Keyframe kf2 = Keyframe.ofFloat(0.4f, -20);
        Keyframe kf3 = Keyframe.ofFloat(0.6f, 20);
        Keyframe kf4 = Keyframe.ofFloat(0.8f, -20);
        Keyframe kf5 = Keyframe.ofFloat(1f, 0);
        PropertyValuesHolder pvhRotation = PropertyValuesHolder.ofKeyframe(View.ROTATION, kf0, kf1, kf2, kf3, kf4, kf5);

        Keyframe kfs0 = Keyframe.ofFloat(0f, 1.0f);
        Keyframe kfs1 = Keyframe.ofFloat(0.5f, 0.97f);
        Keyframe kfs2 = Keyframe.ofFloat(1.0f, 1.0f);
        PropertyValuesHolder pvhScaleX = PropertyValuesHolder.ofKeyframe(View.SCALE_X, kfs0, kfs1, kfs2);
        PropertyValuesHolder pvhScaleY = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, kfs0, kfs1, kfs2);

        shakeAnimation = new AnimatorSet();
        shakeAnimation.playTogether(
                ObjectAnimator.ofPropertyValuesHolder(v, pvhRotation),
                ObjectAnimator.ofPropertyValuesHolder(v, pvhScaleX),
                ObjectAnimator.ofPropertyValuesHolder(v, pvhScaleY));
        shakeAnimation.setDuration(500);
        shakeAnimation.start();
    }

    private void startAppearAnimation() {
        if (animated) {
            return;
        }
        animated = true;
        (scrollView).post(()-> {
            this.setVisibility(VISIBLE);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setDuration(220);

            ObjectAnimator circle1ScaleX = ObjectAnimator.ofFloat(circle, "scaleX", 0, 1);
            ObjectAnimator circle1ScaleY = ObjectAnimator.ofFloat(circle, "scaleY", 0, 1);

            ObjectAnimator circle2ScaleX = ObjectAnimator.ofFloat(circle2, "scaleX", 0, 1);
            ObjectAnimator circle2ScaleY = ObjectAnimator.ofFloat(circle2, "scaleY", 0, 1);

            Log.d("ANIM","MIN " +  scrollView.getMinimumWidth() );
            Log.d("ANIM","START " +  scrollView.getWidth() );

            int  widthMeasureSpec = MeasureSpec.makeMeasureSpec(popupLayout.getWidth() + AndroidUtilities.dp(getMenuMargin()-8), MeasureSpec.AT_MOST);;
        Log.d("ANIM","widthMeasureSpec " +  widthMeasureSpec );
        Log.d("ANIM","popupLayout.getWidth()  " +  popupLayout.getWidth()  );

            ValueAnimator widthAnim = ValueAnimator.ofInt(scrollView.getMinimumWidth(), scrollView.getWidth());
            widthAnim.addUpdateListener(valueAnimator -> {
                int val = (Integer) valueAnimator.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams =  scrollView.getLayoutParams();
                layoutParams.width =  val;
                Log.d("ANIM","UPDATE " +  val );
                scrollView.setLayoutParams(layoutParams);;
            });


            animatorSet.playTogether(circle1ScaleX, circle1ScaleY, circle2ScaleX, circle2ScaleY, widthAnim);
            animatorSet.start();
        });
    }

    private void startShakingAnimations() {
        if ( shakeTimer != null) {
            return;
        }

        shakeTimer = new Timer();
        shakeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                AndroidUtilities.runOnUIThread(() -> {
                    Random rand = new Random();
                    ArrayList<RLottieDrawable> playNow = new ArrayList<>();
                    for (RLottieDrawable drawable : shakeAnimations) {
                        if (rand.nextInt(100) < 25) {
                            playNow.add(drawable);
                        }
                    }

                    if (playNow.size() == 0 && shakeAnimations.size() > 0) {
                        playNow.add(shakeAnimations.get(0));
                    }

                    for (RLottieDrawable drawable : playNow) {
                        drawable.setAutoRepeat(0);
                        drawable.setProgress(0);
                        drawable.start();
                    }

                    shakeAnimations.removeAll(playNow);

                    if (shakeAnimations.size() == 0 && shakeTimer != null) {
                        shakeTimer.cancel();
                        shakeTimer = null;
                    }
                });

            }
        }, 0, 500);
    }


    public void clear() {
        if (shakeTimer != null) {
            shakeTimer.cancel();
            shakeTimer = null;
        }
        shakeAnimations.clear();
    }

    public void setMenu(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout) {
        this.popupLayout = popupLayout;
    }

}