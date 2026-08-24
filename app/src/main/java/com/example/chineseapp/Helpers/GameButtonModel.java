package com.example.chineseapp.Helpers;

public class GameButtonModel {
    private String title;
    private String description;
    private String startText;
    private boolean isPinyinGame; // For specific direct intent logic

    public GameButtonModel(String title, String description, String startText, boolean isPinyinGame) {
        this.title = title;
        this.description = description;
        this.startText = startText;
        this.isPinyinGame = isPinyinGame;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStartText() { return startText; }
    public boolean isPinyinGame() { return isPinyinGame; }
}