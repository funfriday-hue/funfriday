package com.funfriday.games.quizroyale;

import com.funfriday.model.GameAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class QuizRoyaleAction extends GameAction { private String answer; }
