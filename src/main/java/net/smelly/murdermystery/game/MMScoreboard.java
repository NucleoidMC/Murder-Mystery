package net.smelly.murdermystery.game;

import net.minecraft.scoreboard.AbstractTeam.VisibilityRule;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.smelly.murdermystery.MurderMystery;
import net.smelly.murdermystery.game.MMActive.Role;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import com.google.common.collect.Maps;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMScoreboard implements AutoCloseable {
	private final MMActive game;
	private final String mapName;
	private final ServerScoreboard scoreboard;
	private final ScoreboardObjective objective;
	private final EnumMap<Role, Team> roleTeamMap;
	
	public MMScoreboard(MMActive game) {
		this.game = game;
		this.mapName = game.config.mapConfig.name;
		this.scoreboard = game.gameWorld.getWorld().getServer().getScoreboard();
		this.objective = new ScoreboardObjective(
			this.scoreboard, MurderMystery.MOD_ID,
			ScoreboardCriterion.DUMMY,
			new LiteralText("Murder Mystery").formatted(Formatting.GOLD, Formatting.BOLD),
			ScoreboardCriterion.RenderType.INTEGER
		);
		this.scoreboard.addScoreboardObjective(this.objective);
		this.scoreboard.setObjectiveSlot(1, this.objective);
		this.roleTeamMap = this.setupRoleTeamMap();
	}
	
	private EnumMap<Role, Team> setupRoleTeamMap() {
		return Util.make(Maps.newEnumMap(Role.class), (map) -> {
			for (Role role : Role.values()) {
				map.put(role, this.getOrCreateTeam(this.scoreboard, role));
			}
		});
	}
	
	private Team getOrCreateTeam(ServerScoreboard scoreboard, Role role) {
		String name = Role.CACHED_DISPLAYS[role.ordinal()];
		Team team = scoreboard.getTeam(name) != null ? scoreboard.getTeam(name) : scoreboard.addTeam(name);
		team.setNameTagVisibilityRule(VisibilityRule.NEVER);
		return team;
	}
	
	public void addPlayerToRole(ServerPlayerEntity player, Role team) {
		this.scoreboard.addPlayerToTeam(player.getEntityName(), this.roleTeamMap.get(team));
	}
	
	public void tick() {
		if (!this.game.isGameClosing() && this.game.gameWorld.getWorld().getTime() % 10 == 0) this.updateRendering();
	}
	
	public void updateRendering() {
		int ticksTillStart = this.game.ticksTillStart;
		List<String> lines = new ArrayList<>(6);
		
		if (ticksTillStart > 0) {
			lines.add(Formatting.YELLOW + "Starting In: " + Formatting.RESET + this.formatTime(ticksTillStart));
			
			lines.add("");
		} else {
			lines.add(Formatting.RED + "Time Left: " + Formatting.RESET + this.formatTime(this.game.getTimeRemaining()));
			lines.add(Formatting.GREEN + "Innocents Left: " + Formatting.RESET + this.game.getInnocentsRemaining());
			
			lines.add("");
			
			lines.add(Formatting.YELLOW + "Bow Dropped: " + (!this.game.bows.isEmpty() ? Formatting.GREEN + "Yes" : Formatting.RED + "No"));
			
			lines.add(Formatting.RESET.toString());
		}
		
		lines.add("Map: " + this.mapName);
		this.render(this.scoreboard, this.objective, lines.toArray(new String[0]));
	}

	private void render(ServerScoreboard scoreboard, ScoreboardObjective objective, String[] lines) {
		for (ScoreboardPlayerScore score : scoreboard.getAllPlayerScores(objective)) scoreboard.resetPlayerScore(score.getPlayerName(), objective);
		for (int i = 0; i < lines.length; i++) {
			scoreboard.getPlayerScore(lines[i], objective).setScore(lines.length - i);
		}
	}
	
	private String formatTime(long ticks) {
		return String.format("%02d:%02d", ticks / (20 * 60), (ticks / 20) % 60);
	}

	@Override
	public void close() {
		this.roleTeamMap.values().forEach(this.scoreboard::removeTeam);
		this.scoreboard.removeObjective(this.objective);
	}
}