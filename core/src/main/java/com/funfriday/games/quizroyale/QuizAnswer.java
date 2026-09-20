package com.funfriday.games.quizroyale;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class QuizAnswer { private String value; private List<String> aliases; }
