package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.ReactionsController;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class ManageReactionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static int done_button = 1;
    ArrayList<TLRPC.TL_availableReaction> reactions = new ArrayList<>(11);
    ArrayList<String> selectedReactions = new ArrayList<>(11);
    private final long chatId;
    RecyclerListView reactionsListView;
    private TLRPC.ChatFull info;
    private ListAdapter adapter;
    private boolean reactionsEnabled;
    private ActionBarMenuItem doneButton;
    private boolean isEdited;
    private TextCheckCell enableRestrictionsCell;
    private LinearLayout listWrapper;

    public ManageReactionsActivity(long chatId) {
        this.chatId = chatId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("Reactions", R.string.Reactions));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (isEdited) {
                        cancelEdits();
                    } else {
                        finishFragment();
                    }

                } else if (id == done_button) {
                    saveAndExit();
                }
            }
        });


        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
        doneButton.setVisibility(View.INVISIBLE);

        LinearLayout sizeNotifierFrameLayout = new LinearLayout(context);
        sizeNotifierFrameLayout.setOrientation(LinearLayout.VERTICAL);

        fragmentView = sizeNotifierFrameLayout;

        enableRestrictionsCell = new TextCheckCell(context);
        enableRestrictionsCell.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundBlue));
        enableRestrictionsCell.setTypeface(Typeface.DEFAULT_BOLD);
        enableRestrictionsCell.setTextColor(Theme.key_windowBackgroundWhite);
        enableRestrictionsCell.setTextAndCheck(LocaleController.getString("EnableReactions", R.string.EnableReactions), true, false);
    //    enableRestrictionsCell.setChecked(reactionsEnabled);
        enableRestrictionsCell.setOnClickListener(l -> {
            reactionsEnabled = !reactionsEnabled;
            Log.d("EE","cliak H " + reactionsEnabled);
            enableRestrictionsCell.setChecked(reactionsEnabled);
          //  reactionsEnabled = enableRestrictionsCell.isChecked();

            onEdited();
            updateFields();
//            if (adapter != null) {
//                adapter.notifyDataSetChanged();
//            }


        });


        sizeNotifierFrameLayout.addView(enableRestrictionsCell);

        TextInfoPrivacyCell reactionInfoHelp = new TextInfoPrivacyCell(context);
        reactionInfoHelp.setBackgroundColor(Theme.getColor(Theme.key_listSelector));
        reactionInfoHelp.setText(LocaleController.getString("EnableReactionsInfo", R.string.EnableReactionsInfo));


        sizeNotifierFrameLayout.addView(reactionInfoHelp, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listWrapper = new LinearLayout(context);
        listWrapper.setOrientation(LinearLayout.VERTICAL);
      //  listWrapper.setVisibility(reactionsEnabled ? View.VISIBLE : View.GONE);

        HeaderCell headerCellSavingContent = new HeaderCell(context, 23);
        headerCellSavingContent.setText(LocaleController.getString("AvailableReactions", R.string.AvailableReactions));
        listWrapper.addView(headerCellSavingContent);

        reactionsListView = new RecyclerListView(context);
        LinearLayoutManager manager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        reactionsListView.setLayoutManager(manager);
//        reactionsListView.addItemDecoration(new DividerItemDecoration(context,
//                DividerItemDecoration.VERTICAL));
        reactionsListView.setAdapter(adapter = new ListAdapter(context));
        reactionsListView.setVerticalScrollBarEnabled(false);
        reactionsListView.setPadding(0, 0, 0, 0);
        reactionsListView.setClipToPadding(false);
        reactionsListView.setEnabled(true);
        reactionsListView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        reactionsListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // updateLayout();
            }
        });


        listWrapper.addView(reactionsListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        sizeNotifierFrameLayout.addView(listWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ReactionsController.getInstance(currentAccount).loadReactions();
        updateFields();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateFields();
    }

    private void updateFields(){
        if(enableRestrictionsCell != null){
            enableRestrictionsCell.setChecked(reactionsEnabled);
            enableRestrictionsCell.setBackgroundColor(reactionsEnabled?
                    Theme.getColor(Theme.key_avatar_backgroundBlue):
                    Theme.getColor(Theme.key_checkboxDisabled));
        }

        if (listWrapper != null) {
            Log.d("EE","F: " + reactionsEnabled);
            listWrapper.setVisibility(reactionsEnabled ? View.VISIBLE : View.GONE);
        }

        if(enableRestrictionsCell != null){
            enableRestrictionsCell.setChecked(reactionsEnabled);
        }

        if(isEdited){
            if(doneButton != null) {
                doneButton.setVisibility(View.VISIBLE);
            }

            if(actionBar != null) {
                actionBar.setBackButtonImage(R.drawable.ic_close_white);
            }
        }
        else{
            if(doneButton != null) {
                doneButton.setVisibility(View.INVISIBLE);
            }

            if(actionBar != null) {
                actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            }
        }
    }

    private void saveAndExit() {
        try {
            TLRPC.TL_messages_setChatAvailableReactions req = new TLRPC.TL_messages_setChatAvailableReactions();
            Log.d("SS", "R: " + selectedReactions.size());
            // req.available_reactions = new ArrayList<>(selectedReactions.size());
            req.available_reactions.clear();
            if (reactionsEnabled) {
                req.available_reactions.addAll(selectedReactions);
            } else {
                selectedReactions.clear();
            }

            req.peer = getMessagesController().getInputPeer(-chatId);
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    info.available_reactions.clear();
                    info.available_reactions.addAll(selectedReactions);
                    //     getMessagesController().loadFullChat(chatId, 0, true);
                } else {
                    FileLog.e(error.text);
                }
                cancelEdits();
                finishFragment();
            }));


        } catch (Exception e) {
            FileLog.e(e.getMessage());
            finishFragment();
        }

    }

    private void cancelEdits() {
        isEdited = false;
//        doneButton.setVisibility(View.INVISIBLE);
//        actionBar.setBackButtonImage(R.drawable.ic_ab_back);

        if (info != null) {
            reactionsEnabled = info.available_reactions.size() > 0;
            if (enableRestrictionsCell != null) {
                enableRestrictionsCell.setChecked(reactionsEnabled);
            }
            selectedReactions.clear();
            selectedReactions.addAll(info.available_reactions);
            adapter.notifyDataSetChanged();
        }

        updateFields();
    }


    private void onEdited() {
        if (isEdited) {
            return;
        }

        isEdited = true;
//        doneButton.setVisibility(View.VISIBLE);
//        actionBar.setBackButtonImage(R.drawable.ic_close_white);


    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didLoadReactions);
        return super.onFragmentCreate();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didLoadReactions) {
            reactions.clear();
            reactions.addAll(ReactionsController.getInstance(currentAccount).getAllReactions());
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didLoadReactions);
    }

    public void setInfo(TLRPC.ChatFull info) {
        this.info = info;
        if (info != null) {
            reactionsEnabled = info.available_reactions.size() > 0;
            selectedReactions.clear();
            selectedReactions.addAll(info.available_reactions);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        public ArrayList<ReactionCell> getItems() {
            return items;
        }

        private ArrayList<ReactionCell> items = new ArrayList<>(11);
        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return reactions.size();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }


        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            View view;
            view = new ReactionCell(context, null);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            // long did = MessageObject.getPeerId(reactions.get(position));
            TLRPC.TL_availableReaction reaction = reactions.get(position);
            ReactionCell cell = (ReactionCell) holder.itemView;
            cell.setReaction(reactions.get(position));
            cell.textCheckCell.setOnClickListener(l -> {
                cell.textCheckCell.setChecked(!cell.textCheckCell.isChecked());
                if (!cell.textCheckCell.isChecked()) {
                    selectedReactions.remove(reaction.reaction);
                } else {
                    selectedReactions.add(reaction.reaction);
                }
                onEdited();
                updateFields();
            });
            cell.textCheckCell.setEnabled(reactionsEnabled, null);
            cell.textCheckCell.setChecked(selectedReactions.contains(reaction.reaction));

            items.add(cell);
        }
    }


    private class ReactionCell extends FrameLayout {
        BackupImageView imageView;
      //  private final TextView emojiTextView;
        private final TextCheckCell textCheckCell;

        private final Theme.ResourcesProvider resourcesProvider;

        public ReactionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
        //    setWillNotDraw(false);


            imageView = new BackupImageView(context);

            textCheckCell = new TextCheckCell(context);

//            emojiTextView = new TextView(context);
//            emojiTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
//            emojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
//            emojiTextView.setMaxLines(1);
//            emojiTextView.setGravity(Gravity.CENTER);
//            emojiTextView.setLines(1);
//            emojiTextView.setEllipsize(TextUtils.TruncateAt.END);

           // addView(emojiTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.LEFT, 15, 0, 0, 0));
            addView(imageView, LayoutHelper.createFrame(AndroidUtilities.dp(18), AndroidUtilities.dp(18), Gravity.CENTER | Gravity.LEFT, 15, 0, 0, 0));
            addView(textCheckCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER|Gravity.RIGHT, 48, 0, 0, 0));
        }


        public void setReaction(TLRPC.TL_availableReaction reaction) {
           // emojiTextView.setText(reaction.reaction);
            textCheckCell.setTextAndCheck(reaction.title, selectedReactions.contains(reaction.reaction), true);

            TLRPC.Document doc = reaction.static_icon;
            SvgHelper.SvgDrawable svgThumb;
            svgThumb = DocumentObject.getSvgThumb(doc, Theme.key_windowBackgroundGray, 1f);
            imageView.setImage(ImageLocation.getForDocument(doc), null, "webp", svgThumb, reaction);
        }


        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return super.drawChild(canvas, child, drawingTime);
        }


        private int getThemedColor(String key) {
            Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
            return color != null ? color : Theme.getColor(key);
        }
    }
}
