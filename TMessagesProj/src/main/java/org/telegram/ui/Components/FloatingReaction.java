package org.telegram.ui.Components;

import static org.telegram.ui.Components.ReactionsListBubble.getCellSize;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;

public class FloatingReaction extends FrameLayout {

    private final TLRPC.TL_availableReaction reaction;
    private final ChatMessageCell messageCell;
    private final Point screenSize;
    private AnimatorSet animationSet;
    BackupImageView activate_animation;
    BackupImageView effect_animation;
    private Runnable dismissDelegate;
    private AnimatorSet aSet;
    private final ChatActivity chatActivity;
    private RectF clipRect = new RectF();
    boolean applyClip;

    public FloatingReaction(@NonNull Context context, TLRPC.TL_availableReaction reaction, ChatMessageCell cell, Point location, ChatActivity chatActivity, Drawable prev) {
        super(context);
        this.reaction = reaction;
        this.messageCell = cell;
        this.chatActivity = chatActivity;

         screenSize = AndroidUtilities.getRealScreenSize();

        this.setOnClickListener(v -> {
            dismiss();
        });

        this.postDelayed(this::dismiss,6000);

//        RLottieDrawable anim = imageView.getImageReceiver().getLottieAnimation();
//        if (anim != null) {
//            anim.stop();
//            anim.setProgress(0);
//        }


//        int[] outLocation = new int[2];
//        imageView.getLocationOnScreen(outLocation);
     //   Point realLocation = AndroidUtilities.getRealLocationOnScreen(context,imageView);

        int startX = location.x;//outLocation[0];
        int startY = location.y;//outLocation[1];

//        if(isLandscape()){
//
//            final Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
//            if(display.getRotation() == Surface.ROTATION_270){
//                startX -=  getNavigationBarHeight(getContext(),Configuration.ORIENTATION_LANDSCAPE);
//            }
//            else if(display.getRotation() == Surface.ROTATION_90){
//                startX -=  getStatusBarHeight();
//            }
//        }

        //   AndroidUtilities.get

        activate_animation = new BackupImageView(context);
        effect_animation = new BackupImageView(context);


        Point cellSize = getCellSize();

        float reactionSize = AndroidUtilities.dp(ReactionsListBubble.reactionSize);

        //  int currentSize = AndroidUtilities.dp(60);
        float minSize = Math.min(screenSize.x, screenSize.y);
        float scaleX = (minSize * 2f / 5f) / reactionSize;
        float scaleY = (minSize * 2f / 5f) / reactionSize;


        float centerX = chatActivity.contentView.getWidth() / 2f;
        float centerY = chatActivity.contentView.getHeight() / 2f;
        boolean isLandscape = isLandscape();
        if (chatActivity.isKeyboardVisible() && isLandscape) {
            //display on screen center over keyboard.
            centerX = screenSize.x / 2f;
            centerY = screenSize.y / 2f;
        }

        addView(activate_animation, LayoutHelper.createFrame(ReactionsListBubble.reactionSize, ReactionsListBubble.reactionSize));

        activate_animation.setX(startX);
        activate_animation.setY(startY);
       // activate_animation.setY(startY - getStatusBarHeight());

        float dp = AndroidUtilities.convertPxToDp(minSize, context);
        float hm = (screenSize.x - dp) / 2f;
        int actionBarHeight = chatActivity.getActionBar() != null? chatActivity.getActionBar().getBottom():0;
        float vm = (((chatActivity.isKeyboardVisible() && isLandscape) ? screenSize.y : chatActivity.contentView.getHeight())  -actionBarHeight -  getStatusBarHeight()) / 2f;
        //this.addView(effect_animation, LayoutHelper.createFrame((int) dp, dp, Gravity.CENTER, hm, vm, hm, vm));
        this.addView(effect_animation, LayoutHelper.createFrame((int) dp, (int)dp));
        effect_animation.setX(screenSize.x /2f - dp);
        effect_animation.setY(vm - dp/2f);


        activate_animation.setImage(ImageLocation.getForDocument(reaction.activate_animation), null, "tgs",prev, reaction);
        effect_animation.setImage(ImageLocation.getForDocument(reaction.effect_animation), null, "tgs", null, reaction);

      //  imageView.setVisibility(INVISIBLE);

        activate_animation.getImageReceiver().setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {

                // imageReceiver.setImageCoords(0,0,reactionSize,reactionSize);
            }

            @Override
            public void onAnimationReady(ImageReceiver imageReceiver) {
                RLottieDrawable anim = activate_animation.getImageReceiver().getLottieAnimation();
                if (anim != null) {
                    anim.setAutoRepeat(0);
                    anim.setProgress(0);
                    anim.start();
                    anim.setOnFinishCallback(() -> {
                        if (messageCell != null) {
                            Rect pos = messageCell.getPositionForNewReaction(reaction.reaction);
                            if (pos == null) {
                                dismiss();
                                return;
                            }


                            applyClip = true;
                            invalidate();

                            ObjectAnimator animationMoveX = ObjectAnimator.ofFloat(activate_animation, "translationX", pos.left);
                            ObjectAnimator animationMoveY = ObjectAnimator.ofFloat(activate_animation, "translationY", pos.top );
                           // ObjectAnimator animationMoveY = ObjectAnimator.ofFloat(activate_animation, "translationY", pos.top - getStatusBarHeight());
                            ObjectAnimator animationScaleX = ObjectAnimator.ofFloat(activate_animation, "scaleX", pos.right / reactionSize);
                            ObjectAnimator animationScaleY = ObjectAnimator.ofFloat(activate_animation, "scaleY", pos.bottom / reactionSize);


                            aSet = new AnimatorSet();
                            aSet.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    super.onAnimationEnd(animation);
                                    dismiss();
                                }
                            });

                            aSet.playTogether(animationMoveX, animationMoveY, animationScaleX, animationScaleY);
                            aSet.setDuration(220);
                            aSet.start();

                        }
                    }, anim.getFramesCount() - 1);
                }
            }
        });

        effect_animation.getImageReceiver().setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {

            }

            @Override
            public void onAnimationReady(ImageReceiver imageReceiver) {
                RLottieDrawable anim = effect_animation.getImageReceiver().getLottieAnimation();
                if (anim != null) {
                    anim.setAutoRepeat(0);
                    anim.setProgress(0);
                    anim.start();
                    anim.setOnFinishCallback(() -> {
                        //    dismiss();
                    }, anim.getFramesCount() - 1);
                }
            }
        });

        int duration = 220;

        ObjectAnimator animationMoveX = ObjectAnimator.ofFloat(activate_animation, "translationX", centerX - reactionSize / 2f);
        ObjectAnimator animationMoveY = ObjectAnimator.ofFloat(activate_animation, "translationY", centerY - reactionSize / 2f);
        ObjectAnimator animationScaleX = ObjectAnimator.ofFloat(activate_animation, "scaleX", scaleX);
        ObjectAnimator animationScaleY = ObjectAnimator.ofFloat(activate_animation, "scaleY", scaleY);


        animationSet = new AnimatorSet();
        animationSet.playTogether(animationMoveX, animationMoveY, animationScaleX, animationScaleY);
        animationSet.setDuration(duration);
        animationSet.start();
    }

    boolean isLandscape(){
       return AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if(applyClip && child == activate_animation) {
            float clipTop = 0;
            float clipBottom = screenSize.y;

            if (chatActivity.getChatActivityEnterView() != null &&  !(chatActivity.isKeyboardVisible() &&  isLandscape())) {
                clipBottom =chatActivity.getChatActivityEnterView().getTop() - getStatusBarHeight();
            }
            if (chatActivity.getActionBar() != null) {
                clipTop = chatActivity.getActionBar().getBottom()-  getStatusBarHeight();
            }

            clipRect.set(0, clipTop, screenSize.x, clipBottom);

            canvas.save();
            canvas.clipRect(clipRect);
             super.drawChild(canvas, child, drawingTime);
             canvas.restore();
             return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }



    public void dismiss() {
        if (animationSet != null) {
            animationSet.cancel();
        }

        if (animationSet != null) {
            animationSet.cancel();
        }
        if (messageCell != null) {
            messageCell.setAwaitForReaction(null);
        }


        this.post(() -> {
            if (dismissDelegate != null) {
                dismissDelegate.run();
            }
        });

    }

    private int getNavigationBarHeight(Context context, int orientation) {
        Resources resources = context.getResources();

        int id = resources.getIdentifier(
                orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_height_landscape",
                "dimen", "android");
        if (id > 0) {
            return resources.getDimensionPixelSize(id);
        }
        return 0;
    }

    public int getStatusBarHeight() {

        return AndroidUtilities.getStatusBarHeight(getContext());
//        int result = 0;
//        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
//        if (resourceId > 0) {
//            result = getResources().getDimensionPixelSize(resourceId);
//        }
//        return result;
    }

    public void setDismissListener(Runnable r) {
        this.dismissDelegate = r;
    }
}
