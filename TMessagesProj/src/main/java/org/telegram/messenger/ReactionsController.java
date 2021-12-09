package org.telegram.messenger;


import android.util.Log;

import androidx.collection.ArrayMap;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

public class ReactionsController extends BaseController {
    private static volatile ReactionsController[] Instance = new ReactionsController[UserConfig.MAX_ACCOUNT_COUNT];

    public static ReactionsController getInstance(int num) {
        ReactionsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (ReactionsController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new ReactionsController(num);
                }
            }
        }
        return localInstance;
    }

    private final String heartEmoji = "❤";
    private final String thumbUpEmoji = "\uD83D\uDC4D";
    private long lastUpdate = 0;
    private boolean isLoading;
    private int hash = 0;
    //TODO replace with sorted.
    private ArrayList<TLRPC.TL_availableReaction> allReactions = new ArrayList<>(11);

    private ArrayMap<String, TLRPC.TL_availableReaction> sortedReactions;

    public ArrayList<TLRPC.TL_availableReaction> getAllReactions() {
        return allReactions;
    }

    public ArrayMap<String, TLRPC.TL_availableReaction> getSortedReactions() {
        if (sortedReactions == null){
            sortedReactions = new ArrayMap<>(11);
        }

        if (sortedReactions.size() != allReactions.size()) {
            sortedReactions.clear();
            for (TLRPC.TL_availableReaction item : allReactions) {
                sortedReactions.put(item.reaction, item);
            }
        }
        return sortedReactions;
    }

    public ReactionsController(int num) {
        super(num);
    }

    public void loadReactions() {
        if (isLoading) {
            return;
        }

        long currentTime = System.currentTimeMillis() / 1000L;
        if (currentTime - lastUpdate < 3600 && allReactions.size() > 0) {
            getNotificationCenter().postNotificationName(NotificationCenter.didLoadReactions);
            return;
        }

        lastUpdate = currentTime;
        isLoading = true;

        try {
            TLRPC.TL_messages_getAvailableReactions req = new TLRPC.TL_messages_getAvailableReactions();
            req.hash = hash;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                isLoading = false;
                if (error == null) {
                    TLRPC.messages_AvailableReactions res = (TLRPC.messages_AvailableReactions) response;
                    if (res instanceof TLRPC.TL_messages_availableReactionsNotModified) {
                        AndroidUtilities.runOnUIThread(() -> {
                            getNotificationCenter().postNotificationName(NotificationCenter.didLoadReactions);
                        });
                    } else if (res instanceof TLRPC.TL_messages_availableReactions) {
                        TLRPC.TL_messages_availableReactions availableReactions = (TLRPC.TL_messages_availableReactions) res;
                        hash = availableReactions.hash;
                        processReactions(availableReactions.reactions);
                        AndroidUtilities.runOnUIThread(() -> {
                            getNotificationCenter().postNotificationName(NotificationCenter.didLoadReactions);
                        });
                    }
                } else {
                    FileLog.e(error.text);
                }
            });

        } catch (Exception ex) {
            isLoading = false;
            FileLog.e(ex.getMessage());
        }
    }


    public void processReactions(ArrayList<TLRPC.TL_availableReaction> reactions) {
        if (allReactions == null) {
            allReactions = new ArrayList<>(reactions.size());
        }
        allReactions.clear();
        if(sortedReactions != null) {
            sortedReactions.clear();
        }
        getSortedReactions();
        allReactions.addAll(reactions);
    }

    public void sendReaction(String reaction, int msg_id, TLRPC.InputPeer peer, ArrayList<String> available_reactions, MessagesStorage.BooleanCallback callback) {
        try {
            if (available_reactions != null && !available_reactions.contains(reaction)) {
                if (callback != null) {
                    callback.run(false);
                }
                return;
            }

            TLRPC.TL_messages_sendReaction req = new TLRPC.TL_messages_sendReaction();
            req.flags |= 1;
            req.peer = peer;
            req.reaction = reaction;
            req.msg_id = msg_id;

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    if (response != null) {
                        MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) response, false);
                    }

                    if (callback != null) {
                        callback.run(true);
                    }
                } else {
                    FileLog.e(error.text);
                    if (callback != null) {
                        callback.run(false);
                    }
                }
            }));

        } catch (Exception ex) {
            FileLog.e(ex.getMessage());
            if (callback != null) {
                callback.run(false);
            }
        }
    }

    public boolean sendQuickReaction(MessageObject msg, TLRPC.ChatFull info) {
        if (msg == null) {
            return false;
        }

        if (msg.messageOwner.reactions != null && msg.messageOwner.reactions.results != null) {
            for (int i = 0; i < msg.messageOwner.reactions.results.size(); ++i) {
                TLRPC.TL_reactionCount c = msg.messageOwner.reactions.results.get(i);
                if (c.chosen) {
                    ReactionsController.getInstance(currentAccount)
                            .sendReaction("", msg.getId(),
                                    MessagesController.getInstance(currentAccount).getInputPeer(msg.messageOwner.peer_id),
                                    null, null);
                    return true;
                }
            }
        }

        String reaction = heartEmoji;
        if (info != null) {
            if (info.available_reactions.contains(heartEmoji)) {
                reaction = heartEmoji;
            } else if (info.available_reactions.contains(thumbUpEmoji)) {
                reaction = thumbUpEmoji;
            } else {
                return false;
            }
        }

        this.sendReaction(reaction, msg.getId(),
                MessagesController.getInstance(currentAccount).getInputPeer(msg.messageOwner.peer_id),
                null, null);

        return false;
    }

    public int totalCount() {
        return allReactions.size();
    }
}