package com.example.chineseapp.Helpers;

import android.content.Context;
import android.content.Intent;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.chineseapp.R;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GamesButtonAdapter extends RecyclerView.Adapter<GamesButtonAdapter.ViewHolder> {

    private Context context;
    private List<GameButtonModel> gameList;
    private int expandedIndex = -1;
    private RecyclerView recyclerView;

    public GamesButtonAdapter(Context context, List<GameButtonModel> gameList) {
        this.context = context;
        this.gameList = gameList;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView; // Captured to smoothly animate parent bounds
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_game_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GameButtonModel game = gameList.get(position);
        holder.bind(game, position);
    }

    @Override
    public int getItemCount() {
        return gameList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ConstraintLayout cardLayout;
        TextView titleTv, descriptionTv, startBtnText;
        LinearLayout actionButton;
        ImageView arrowIcon;

        ConstraintSet unexpandedSet = new ConstraintSet();
        ConstraintSet expandedSet = new ConstraintSet();

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.game_card);
            cardLayout = itemView.findViewById(R.id.card_layout);
            titleTv = itemView.findViewById(R.id.game_title);
            descriptionTv = itemView.findViewById(R.id.game_description);
            actionButton = itemView.findViewById(R.id.action_button);
            startBtnText = itemView.findViewById(R.id.start_button_text);
            arrowIcon = itemView.findViewById(R.id.arrow_icon);

            unexpandedSet.clone(cardLayout);
            expandedSet.clone(context, R.layout.item_game_card_expanded);
        }

        public void bind(GameButtonModel game, int position) {
            titleTv.setText(game.getTitle());
            descriptionTv.setText(game.getDescription());

            boolean isExpanded = position == expandedIndex;

            // Apply layouts immediately during continuous scrolling binds
            ConstraintSet targetSet = isExpanded ? expandedSet : unexpandedSet;
            targetSet.applyTo(cardLayout);

            actionButton.setOnClickListener(v -> {
                launchGameActivity(game);
            });

            cardView.setOnClickListener(v -> {
                if (position == expandedIndex) {
                    animateStateChange(position, false);
                } else {
                    int previouslyExpanded = expandedIndex;
                    expandedIndex = position;

                    if (previouslyExpanded != -1) {
                        RecyclerView.ViewHolder oldHolder = recyclerView.findViewHolderForAdapterPosition(previouslyExpanded);
                        if (oldHolder instanceof ViewHolder) {
                            ((ViewHolder) oldHolder).animateStateChange(previouslyExpanded, false);
                        } else {
                            notifyItemChanged(previouslyExpanded);
                        }
                    }

                    animateStateChange(position, true);
                }
            });
        }

        /**
         * Orchestrates high-speed layout morphing and parent height adjustments.
         */
        public void animateStateChange(int position, boolean expand) {
            if (!expand && expandedIndex == position) {
                expandedIndex = -1;
            }

            // Custom transition set built for raw performance and high speed
            TransitionSet fastTransition = new TransitionSet();
            fastTransition.addTransition(new ChangeBounds()); // Smoothly morph button shape and tile height
            fastTransition.addTransition(new Fade(Fade.IN));   // Clean entry for description and "Start" text
            fastTransition.addTransition(new Fade(Fade.OUT));  // Clean exit when collapsing
            fastTransition.setDuration(160); // PROBLEM 1 & 3 FIX: Cut duration in half to double execution speed

            // CRITICAL HEIGHT FIX: Passing the parent recyclerView forces the layout engine
            // to capture and smoothly animate the changing layout bounds of the entire tile row.
            TransitionManager.beginDelayedTransition(recyclerView, fastTransition);

            // Execute constraints changes
            ConstraintSet targetSet = expand ? expandedSet : unexpandedSet;
            targetSet.applyTo(cardLayout);
        }

        private void launchGameActivity(GameButtonModel game) {
            Toast.makeText(context, "Directing to activity straight...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(context, PinyinGameActivity.class);
            intent.putExtra("game_title", game.getTitle());
            context.startActivity(intent);
        }
    }
}