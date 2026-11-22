package com.funfriday.games.logicgrid;
import com.funfriday.model.GameAction; import lombok.*; import java.util.Map;
@Data @EqualsAndHashCode(callSuper=true) public class LogicGridAction extends GameAction { private Map<String,Map<String,String>> answers; }
