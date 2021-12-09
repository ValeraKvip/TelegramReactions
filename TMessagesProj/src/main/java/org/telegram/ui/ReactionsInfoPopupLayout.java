package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Random;

public class ReactionsInfoPopupLayout extends LinearLayout {
    private final ReactionsPagerAdapter adapter;
    private final ViewPager viewPager;
    private final MessageObject message;

    ArrayList<Tab> tabs = new ArrayList<>(12);
    ArrayList<ReactionPage> pages = new ArrayList<>(12);
    ArrayList<UserData> data = new ArrayList<>();

    Tab selectedTab;
    AnimatorSet selectAnimator;
    private Runnable backDelegate;
    private ChatActivity chatActivity;
    private Runnable dismissDelegate;
    private boolean isAllLoaded;
    private final Random rand = new Random();

    public ReactionsInfoPopupLayout(Context context, ChatActivity chatActivity, MessageObject message, ChatActivity.ThemeDelegate themeDelegate, String filter, int count) {
        // Single page popup.
        super(context);
        this.chatActivity = chatActivity;
        this.message = message;
        adapter = null;
        viewPager = null;

        Drawable shadowDrawable3 = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert).mutate();
        shadowDrawable3.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        setBackground(shadowDrawable3);

        setOrientation(VERTICAL);

        ReactionPage page = new ReactionPage(context,filter,count);
        this.addView(page, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,LayoutHelper.WRAP_CONTENT));
        page.updateValues();
    }


    public ReactionsInfoPopupLayout(Context context, ChatActivity chatActivity, MessageObject message, ChatActivity.ThemeDelegate themeDelegate) {
        super(context);
        this.chatActivity = chatActivity;
        this.message = message;


        setOrientation(VERTICAL);
        setOrientation(LinearLayout.VERTICAL);
        Drawable shadowDrawable3 = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert).mutate();
        shadowDrawable3.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        setBackground(shadowDrawable3);

        ActionBarMenuSubItem cell = new ActionBarMenuSubItem(chatActivity.getParentActivity(), true, true, themeDelegate);
        cell.setItemHeight(44);
        cell.setTextAndIcon(LocaleController.getString("Back", R.string.Back), R.drawable.msg_arrow_back);
        cell.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), 0, LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0);
        cell.setOnClickListener(v -> {
            if (backDelegate != null) {
                backDelegate.run();
            }
        });
        addView(cell);


        HorizontalScrollView scrollViewFilters = new HorizontalScrollView(getContext());
        scrollViewFilters.setClipChildren(true);
        scrollViewFilters.setVerticalScrollBarEnabled(false);
        scrollViewFilters.setHorizontalScrollBarEnabled(false);

        LinearLayout filterLayout = new LinearLayout(getContext());
        filterLayout.setOrientation(LinearLayout.HORIZONTAL);
        filterLayout.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        scrollViewFilters.addView(filterLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));
        addView(scrollViewFilters, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 9));
        View line = new View(getContext());
        line.setBackgroundColor(0xffe5e5e5);

        addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 0, 0, 0, 0));

        int totalReactionsCount = 0;
        ArrayMap<String, TLRPC.TL_availableReaction> availableReactions = chatActivity.getReactionsController().getSortedReactions();
        if (availableReactions != null) {
            if (message != null && message.messageOwner != null && message.messageOwner.reactions.results != null) {

                for (TLRPC.TL_reactionCount item : message.messageOwner.reactions.results) {
                    totalReactionsCount += item.count;

                    TLRPC.TL_availableReaction currentReaction = availableReactions.get(item.reaction);
                    if (currentReaction != null) {
                        Tab tab = new Tab(context, item, currentReaction);
                        tabs.add(tab);
                        filterLayout.addView(tab, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 3, 0, 3, 0));

                        ReactionPage page = new ReactionPage(getContext(), item.reaction, item.count);
                        pages.add(page);
                    }
                }
            }
        }

        Tab tab = new Tab(context, totalReactionsCount, R.drawable.msg_reactions_filled);

        tabs.add(0, tab);
        filterLayout.addView(tab, 0, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 3, 0, 3, 0));
        tab.setSelected();
        ReactionPage page = new ReactionPage(getContext(), null, totalReactionsCount);
        pages.add(0, page);
        data = page.pageData;


        viewPager = new ViewPager(getContext());
        viewPager.setAdapter(adapter = new ReactionsPagerAdapter());
        viewPager.setCurrentItem(0);
        addView(viewPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));


        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                Tab t = tabs.get(position);
                t.select(true);

                final Rect scrollBounds = new Rect();
                scrollViewFilters.getGlobalVisibleRect(scrollBounds);
                if (!(t.getGlobalVisibleRect(scrollBounds)
                        && t.getHeight() == scrollBounds.height()
                        && t.getWidth() == scrollBounds.width())) {
                    scrollViewFilters.smoothScrollTo(t.getLeft(), t.getTop());
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }


    private void loadMore(ReactionPage page) {
        if (page.isLoading || page.isAllLoaded()) {
            return;
        }

        String filter = page.reaction;
        if (filter != null && data.size() > 0 && page.pageData.size() == 0) {
            for (UserData item : data) {
                if (item.isPlaceholder) {
                    break;
                }
                if (item.reaction.equals(filter)) {
                    page.pageData.add(item);
                }
            }

            if (page.pageData.size() != 0) {
                page.notifyDataSetChanged();
                //   return;
            }
        }

        if (page.isAllLoaded()) {
            return;
        }

        if (isAllLoaded) {
            return;
        }

        int limit = filter == null ? 100 : 50;
        int placeholders = limit - page.pageData.size() % limit;
        if (page.pageData.size() + placeholders > page.totalCount) {
            placeholders = page.totalCount - page.pageData.size();
        }

        if (placeholders != 0) {
            for (int i = 0; i < placeholders; ++i) {
                page.pageData.add(new UserData(true));
            }
            page.notifyDataSetChanged();
        }

        ArrayMap<String, TLRPC.TL_availableReaction> availableReactions = chatActivity.getReactionsController().getSortedReactions();

        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.limit = limit;
        req.id = message.getId();

        if (page.nextOffset != null) {
            req.offset = page.nextOffset;
            req.flags |= 2;
        }

        req.peer = chatActivity.getMessagesController().getInputPeer(message.messageOwner.peer_id);
        if (filter != null) {
            req.reaction = filter;
            req.flags |= 1;
        }

        page.isLoading = true;
        int finalPlaceholders = placeholders;


        chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null ) {
                TLRPC.TL_messages_messageReactionsList reactionsList = (TLRPC.TL_messages_messageReactionsList) response;

                ArrayList<UserData> newData = new ArrayList<>(reactionsList.reactions.size());
                int index = 0;
                for (TLRPC.User item : reactionsList.users) {


                    if (filter != null) {
                        TLRPC.TL_availableReaction r = availableReactions.get(filter);
                        newData.add(new UserData(item, filter, r == null ? null : r.static_icon));
                    } else {
                        String reaction =  reactionsList.reactions.get(index).reaction;
                        TLRPC.TL_availableReaction r = availableReactions.get(reaction);
                        newData.add(new UserData(item, reaction, r == null ? null : r.static_icon));
                    }
                    ++index;
                }

                if (page.nextOffset == null) {
                    page.pageData.clear();
                    page.pageData.addAll(newData);
                } else {

                    int size = page.pageData.size() - finalPlaceholders;
                    for (int i = page.pageData.size() - 1; i >= size; --i) {
                        page.pageData.remove(i);
                    }
                    page.pageData.addAll(newData);
                }

                page.nextOffset = reactionsList.next_offset;
                if ((reactionsList.flags & 1) == 0) {
                    page.isAllLoaded = true;
                    if (filter == null) {
                        isAllLoaded = true;
                    }
                }

                if (chatActivity != null) {
                    chatActivity.getMessagesController().putUsers(reactionsList.users, false);
                }
                page.notifyDataSetChanged();


            } else {
                if (error != null) {
                    FileLog.e(error.text);
                }
                int size = page.pageData.size() - finalPlaceholders;
                for (int i = page.pageData.size() - 1; i >= size; --i) {
                    if(!(i >= 0 && i < page.pageData.size())){
                        break;
                    }
                    page.pageData.remove(i);
                }
                page.notifyDataSetChanged();
            }

            page.isLoading = false;
        }));
    }

    public void setOnBackClickListener(Runnable delegate) {
        backDelegate = delegate;
    }

    public void setChatActivity(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
    }

    public void setDismissListener(Runnable delegate) {
        this.dismissDelegate = delegate;
    }

    private class UserData {
        public boolean isPlaceholder;
        public TLRPC.User user;
        public String reaction;
        public TLRPC.Document static_icon;

        public UserData(TLRPC.User user, String reaction, TLRPC.Document static_icon) {
            this.user = user;
            this.reaction = reaction;
            this.static_icon = static_icon;
        }

        public UserData(boolean isPlaceholder) {
            this.isPlaceholder = isPlaceholder;
        }
    }

    private class Tab extends FrameLayout {
        private TextView countTextView;
        BackupImageView imageView;
        int count;
        String reaction;
        final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        Path clip = new Path();
        int outlineOffset = AndroidUtilities.dp(2);
        private float outlineScale;
        private float bubbleScale;

        public Tab(@NonNull Context context) {
            super(context);

            imageView = new BackupImageView(context);
            countTextView = new TextView(context);
            countTextView.setTextSize(12);
            countTextView.setTextColor(0xff378dd1);
            Paint textPaint = Theme.getThemePaint(Theme.key_paint_chatInnerOutReactionButton);

            if (textPaint != null) {
                countTextView.setTextColor(textPaint.getColor());
            }


            addView(countTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 32, 5, 10, 5));

            bg.setColor(Color.parseColor("#ebf3fa"));
            circlePaint.setColor(0xf378dd1);
            circlePaint.setAlpha(50);
            outline.setColor(0xff378dd1);
            outline.setStyle(Paint.Style.STROKE);

            setWillNotDraw(false);

            setOnClickListener(v -> {
                select(false);
            });
        }

        private void updateOutline(float val) {
            outlineScale = val;
            invalidate();
        }

        private void updateBubble(float val) {
            bubbleScale = val;
            invalidate();
        }

        private void setSelected() {
            outlineScale = 1;
            selectedTab = this;
        }

        public Tab(@NonNull Context context, int count, int icon) {
            this(context);
            countTextView.setText(String.valueOf(count));

            Drawable iconDrawable = ContextCompat.getDrawable(getContext(), icon).mutate();
            iconDrawable.setColorFilter(new PorterDuffColorFilter(0xff378dd1, PorterDuff.Mode.MULTIPLY));

            imageView.setBackground(iconDrawable);
            addView(imageView, LayoutHelper.createFrame(22, 22, Gravity.LEFT | Gravity.CENTER, 6, 1, 0, 1));

        }


        public Tab(@NonNull Context context, TLRPC.TL_reactionCount reactionCount, TLRPC.TL_availableReaction currentReaction) {
            this(context);

            if (reactionCount != null) {
                count = reactionCount.count;
                reaction = reactionCount.reaction;
            }

            TLRPC.Document doc = currentReaction.static_icon;
            SvgHelper.SvgDrawable svgThumb;
            svgThumb = DocumentObject.getSvgThumb(doc, Theme.key_windowBackgroundGray, 1f);
            imageView.setImage(ImageLocation.getForDocument(doc), null, "webp", svgThumb, reaction);

            countTextView.setText(String.valueOf(count));
            addView(imageView, LayoutHelper.createFrame(18, 18, Gravity.LEFT, 10, 5, 0, 5));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            rect.set(0, 0, getWidth(), getHeight());

            canvas.drawRoundRect(rect, AndroidUtilities.dp(25), AndroidUtilities.dp(25), bg);

            clip.reset();
            clip.addRoundRect(rect, AndroidUtilities.dp(25), AndroidUtilities.dp(25), Path.Direction.CW);
            clip.close();
            canvas.clipPath(clip);

            if (selectedTab == this && bubbleScale != 0) {


                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() * bubbleScale, circlePaint);

            }

            if (outlineScale != 0) {
                float offset = outlineOffset * outlineScale;
                outline.setStrokeWidth(offset);
           //     rect.set(offset, offset, getWidth() - offset, getHeight() - offset);
                rect.set(0, 0, getWidth() , getHeight());
                canvas.drawRoundRect(rect, AndroidUtilities.dp(25), AndroidUtilities.dp(25), outline);
            }
        }

        public String getReaction() {
            return reaction;
        }


        public void select(boolean fromPager) {
            if (this == selectedTab) {
                return;
            }
            Tab old = selectedTab;
            selectedTab = this;

            old.bubbleScale = 0;
            this.bubbleScale = 0;

            ArrayList<Animator> animList = new ArrayList<>(2);
            if (!fromPager) {
                for (int index = 0; index < tabs.size(); ++index) {
                    if (tabs.get(index) == this) {
                        viewPager.setCurrentItem(index, true);
                        break;
                    }
                }

                this.circlePaint.setAlpha(50);
                ValueAnimator bubble = ValueAnimator.ofFloat(0, 1);
                bubble.addUpdateListener(animation -> {
                    this.updateBubble(animation.getAnimatedFraction());
                });
                bubble.setDuration(150);
                animList.add(bubble);
            }

            old.bubbleScale = 0;


            ValueAnimator border = ValueAnimator.ofFloat(0, 1);
            border.setStartDelay(150);
            border.addUpdateListener(animation -> {
                this.updateOutline(animation.getAnimatedFraction());
                this.circlePaint.setAlpha((int) (50 - 50 * animation.getAnimatedFraction()));
                old.updateOutline(1 - animation.getAnimatedFraction());
            });
            border.setDuration(150);
            animList.add(border);

            if (selectAnimator != null) {
                selectAnimator.end();
            }
            selectAnimator = new AnimatorSet();
            selectAnimator.setDuration(220);

            selectAnimator.playTogether(animList);
            selectAnimator.start();

            selectAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    old.bubbleScale = 0;
                    old.outlineScale = 0;
                    bubbleScale = 0;
                    outlineScale = 1;
                }
            });
        }
    }

    private class ReactionsPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return pages.size();
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            container.addView(pages.get(position));
            pages.get(position).updateValues();
            return pages.get(position);
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((ReactionPage) object);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    private class ReactionPage extends FrameLayout {
        private final RecyclerListView recyclerListView;
        public String nextOffset;
        private final String reaction;
        private final RecyclerListView.SelectionAdapter lAdapter;
        public int totalCount = -1;
        public boolean isLoading;
        public boolean isAllLoaded;
        ArrayList<UserData> pageData = new ArrayList<>();

        public ReactionPage(@NonNull Context context, String reaction, int count) {
            super(context);
            this.reaction = reaction;
            this.totalCount = count;

            recyclerListView = new RecyclerListView(getContext());
            recyclerListView.setLayoutManager(new LinearLayoutManager(getContext()) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
            });
            recyclerListView.setPadding(0, 0, 0, 0);
            recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    int p = parent.getChildAdapterPosition(view);
                    if (p == 0) {
                        outRect.top = AndroidUtilities.dp(4);
                    }
                    if (p == pageData.size() - 1) {
                        outRect.bottom = AndroidUtilities.dp(4);
                    }
                }
            });
            recyclerListView.setAdapter(lAdapter = new RecyclerListView.SelectionAdapter() {
                private final int TYPE_PLACEHOLDER = 1;

                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @Override
                public int getItemViewType(int position) {
                    UserData item = pageData.get(position);
                    return item.isPlaceholder?TYPE_PLACEHOLDER:2;
                }

                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    if(viewType == TYPE_PLACEHOLDER) {
//                        PlaceHolderCell placeHolderCell = new PlaceHolderCell(parent.getContext(), parent);
//                        placeHolderCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
//                        return new RecyclerListView.Holder(placeHolderCell);
                        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(parent.getContext());
                        flickerLoadingView.setIsSingleCell(true);
                        flickerLoadingView.setViewType(FlickerLoadingView.REACTION_TYPE);
                        flickerLoadingView.setRandomLen(AndroidUtilities.dp(rand.nextInt(5) + 1));
                        flickerLoadingView.setPaddingLeft(AndroidUtilities.dp(0));
                      //  flickerLoadingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        flickerLoadingView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        return new RecyclerListView.Holder(flickerLoadingView);

                    }else{
                        UserCell userCell = new UserCell(parent.getContext());
                        userCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        return new RecyclerListView.Holder(userCell);
                    }
                }

                @Override
                public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    if(holder.itemView instanceof UserCell) {
                        UserCell cell = (UserCell) holder.itemView;
                        UserData item = pageData.get(position);
                        if (item.isPlaceholder) {
                           // cell.setPlaceholder(item);
                        } else {
                            cell.setUser(item);
                        }
                    }
                    else{
                        FlickerLoadingView flickerLoadingView = (FlickerLoadingView) holder.itemView;
                        flickerLoadingView.setItemsCount(1);
                    }

                    holder.itemView.setAlpha(0);
                    long delay = position*5L;
                    holder.itemView.animate().alpha(1).setDuration(100).start();
                }

                @Override
                public int getItemCount() {
                    return pageData.size();
                }
            });

            recyclerListView.setOnItemClickListener((view, position) -> {
                if (chatActivity == null) {
                    return;
                }

                TLRPC.User user = pageData.get(position).user;
                if (user == null) {
                    return;
                }
                Bundle args = new Bundle();
                args.putLong("user_id", user.id);
                ProfileActivity fragment = new ProfileActivity(args);
                chatActivity.presentFragment(fragment);
                if (dismissDelegate != null) {
                    dismissDelegate.run();
                }

            });

            recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);

                    if (!recyclerListView.canScrollVertically(1)) {
                        loadMore(ReactionPage.this);
                    }
                }
            });

            this.addView(recyclerListView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
        }



        private void updateValues() {
            if (!isAllLoaded()) {
                loadMore(this);
            }
        }

        public void notifyItemRangeChanged(int  pos, int count) {
            // recyclerListView.getRecycledViewPool().clear();
            lAdapter.notifyItemRangeChanged(pos, count);
        }


        public void notifyDataSetChanged() {
           // recyclerListView.getRecycledViewPool().clear();
            lAdapter.notifyDataSetChanged();
        }

        public boolean isAllLoaded() {
            return isAllLoaded || totalCount <= pageData.size();
        }

//        private class PlaceHolderCell extends FrameLayout {
//            private final ViewGroup parent;
//            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
//            RectF rectF = new RectF();
//            Random rand = new Random();
//
//
//            @Override
//            protected void onDraw(Canvas canvas) {
//                super.onDraw(canvas);
//                canvas.drawCircle(AndroidUtilities.dp(29),getHeight()/2f,AndroidUtilities.dp(16),bg);
//                canvas.drawCircle( getWidth() - AndroidUtilities.dp(17),getHeight()/2f,AndroidUtilities.dp(8),bg);
//
//                int w = AndroidUtilities.dp(rand.nextInt(5) + 1);
//                int maxW = (getMeasuredWidth() - AndroidUtilities.dp(96) - AndroidUtilities.dp(25) - w*2)/2;
//
//                rectF.set( AndroidUtilities.dp(56),getHeight()/2f - AndroidUtilities.dp(4),
//                        AndroidUtilities.dp(56) + maxW ,getHeight()/2f + AndroidUtilities.dp(4));
//
//                canvas.drawRoundRect(rectF,25,25, bg);
//
//                rectF.set(AndroidUtilities.dp(60) + maxW ,getHeight()/2f - AndroidUtilities.dp(4),
//                        AndroidUtilities.dp(60) + maxW*2,getHeight()/2f + AndroidUtilities.dp(4));
//
//                canvas.drawRoundRect(rectF,25,25, bg);
//
//            }
//
//            public PlaceHolderCell(Context context,ViewGroup parent) {
//                super(context);
//                this.parent = parent;
//                bg.setColor(0xfff0f1f2);
//                setBackgroundColor(Color.TRANSPARENT);
//               // setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
//            }
//
//            @Override
//            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//                super.onMeasure(MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY));
//            }
//
//        }

            private class UserCell extends FrameLayout {
            BackupImageView avatarImageView;
            BackupImageView reactionImageView;
            TextView nameView;
            AvatarDrawable avatarDrawable = new AvatarDrawable();

            public UserCell(Context context) {
                super(context);
                //setBackgroundColor(Color.GREEN);
                avatarImageView = new BackupImageView(context);
                addView(avatarImageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL, 13, 0, 0, 0));
                avatarImageView.setRoundRadius(AndroidUtilities.dp(16));
                nameView = new TextView(context);
                nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                nameView.setLines(1);
                nameView.setEllipsize(TextUtils.TruncateAt.END);
                addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 59, 0, 43, 0));

                nameView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

                reactionImageView = new BackupImageView(context);
                addView(reactionImageView, LayoutHelper.createFrame(20, 20, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 13, 0));
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), View.MeasureSpec.EXACTLY));
            }

            public void setUser(UserData userData) {
                if (userData != null) {
                    if (userData.user != null) {
                        avatarDrawable.setInfo(userData.user);
                        ImageLocation imageLocation = ImageLocation.getForUser(userData.user, ImageLocation.TYPE_SMALL);
                        avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, userData.user);
                        nameView.setText(ContactsController.formatName(userData.user.first_name, userData.user.last_name));

                        nameView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
                        nameView.setBackground(null);
                        nameView.setBackgroundColor(0x00000000);
                    }

                    if (userData.static_icon != null) {
                        SvgHelper.SvgDrawable svgThumb;
                        svgThumb = DocumentObject.getSvgThumb(userData.static_icon, Theme.key_windowBackgroundGray, 1f);
                        reactionImageView.setImage(ImageLocation.getForDocument(userData.static_icon), null, "webp", svgThumb, reaction);

                        reactionImageView.setRoundRadius(0);
                        reactionImageView.setBackgroundColor(0x00000000);
                    }
                }
            }
        }
    }
}
