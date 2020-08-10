package net.smelly.murdermystery.game;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.smelly.murdermystery.MurderMystery;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryScoreboard {
	private final MurderMysteryActive game;
	private final ScoreboardObjective objective;
	
	public MurderMysteryScoreboard(MurderMysteryActive game) {
		this.game = game;
		
		ServerScoreboard scoreboard = game.gameWorld.getWorld().getServer().getScoreboard();
		ScoreboardObjective objective = new ScoreboardObjective(
			scoreboard, MurderMystery.MOD_ID,
			ScoreboardCriterion.DUMMY,
			new LiteralText("Murder Mystery").formatted(Formatting.GOLD, Formatting.BOLD),
			ScoreboardCriterion.RenderType.INTEGER
		);
		scoreboard.addScoreboardObjective(objective);
		scoreboard.setObjectiveSlot(1, objective);
		
		this.objective = objective;
	}
	
	public void tick() {
		if (this.game.gameWorld.getWorld().getTime() % 10 == 0) this.rerender();
	}
	
	private void rerender() {
		List<String> lines = new ArrayList<>(10);
		
		lines.add("");
		
		lines.add(Formatting.RED.toString() + Formatting.BOLD + "Time left: " + Formatting.RESET + this.renderTime(this.game.getTimeRemaining()));
		
		lines.add("");
		
		lines.add(Formatting.YELLOW.toString() + Formatting.BOLD + "Bow Dropped: " + (!this.game.bows.isEmpty() ? Formatting.GREEN + "Yes" : Formatting.RED + "No"));
		
		this.render(lines.toArray(new String[0]));
	}
	
	private void render(String[] lines) {
		ServerScoreboard scoreboard = this.game.gameWorld.getWorld().getServer().getScoreboard();
		this.render(scoreboard, this.objective, lines);
	}
	
	private void render(ServerScoreboard scoreboard, ScoreboardObjective objective, String[] lines) {
		for (ScoreboardPlayerScore score : scoreboard.getAllPlayerScores(objective)) scoreboard.resetPlayerScore(score.getPlayerName(), objective);
		for (int i = 0; i < lines.length; i++) scoreboard.getPlayerScore(lines[i], objective).setScore(lines.length - i);
    }
	
	private String renderTime(long ticks) {
		return String.format("%02d:%02d", ticks / (20 * 60), (ticks / 20) % 60);
	}
}