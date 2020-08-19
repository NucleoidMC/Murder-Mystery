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
import net.minecraft.util.Pair;
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
	private final EnumMap<Role, Pair<Team, ScoreboardObjective>> roleScoreboardMap;
	private final ScoreboardObjective startingObjective;
	
	public MMScoreboard(MMActive game) {
		this.game = game;
		this.mapName = game.config.mapConfig.name;
		this.scoreboard = game.gameWorld.getWorld().getServer().getScoreboard();
		this.roleScoreboardMap = this.setupRoleScoreboardMap();
		this.startingObjective = new ScoreboardObjective(
			this.scoreboard, MurderMystery.MOD_ID,
			ScoreboardCriterion.DUMMY,
			new LiteralText("Murder Mystery").formatted(Formatting.GOLD, Formatting.BOLD),
			ScoreboardCriterion.RenderType.INTEGER
		);
		this.scoreboard.addScoreboardObjective(this.startingObjective);
		this.scoreboard.setObjectiveSlot(1, this.startingObjective);
	}
	
	private EnumMap<Role, Pair<Team, ScoreboardObjective>> setupRoleScoreboardMap() {
		return Util.make(Maps.newEnumMap(Role.class), (map) -> {
			for (Role role : Role.values()) {
				ScoreboardObjective objective = new ScoreboardObjective(
					this.scoreboard, role.name(),
					ScoreboardCriterion.DUMMY,
					new LiteralText("Murder Mystery").formatted(Formatting.GOLD, Formatting.BOLD),
					ScoreboardCriterion.RenderType.INTEGER
				);
				this.scoreboard.addScoreboardObjective(objective);
				this.scoreboard.setObjectiveSlot(3 + role.getDisplayColor().getColorIndex(), objective);
				map.put(role, new Pair<>(this.getOrCreateTeam(this.scoreboard, role), objective));
			}
		});
	}
	
	private Team getOrCreateTeam(ServerScoreboard scoreboard, Role role) {
		String name = Formatting.RESET + Role.CACHED_DISPLAYS[role.ordinal()];
		Team team = scoreboard.getTeam(name) != null ? scoreboard.getTeam(name) : scoreboard.addTeam(name);
		team.setColor(role.getDisplayColor());
		team.setNameTagVisibilityRule(VisibilityRule.NEVER);
		return team;
	}
	
	public void addPlayerToRole(ServerPlayerEntity player, Role team) {
		this.scoreboard.addPlayerToTeam(player.getEntityName(), this.roleScoreboardMap.get(team).getLeft());
	}
	
	public void tick() {
		if (this.game.ticksTillClose < 0 && this.game.gameWorld.getWorld().getTime() % 10 == 0) this.updateRendering();
	}
	
	public void updateRendering() {
		this.roleScoreboardMap.forEach((role, teamAndObjective) -> {
			List<String> lines = new ArrayList<>(8);
			
			lines.add("Role: " + Role.CACHED_DISPLAYS[role.ordinal()]);
			
			lines.add("");
			
			lines.add(Formatting.RED + "Time Left: " + Formatting.RESET + this.formatTime(this.game.getTimeRemaining()));
			lines.add(Formatting.GREEN + "Innocents Left: " + Formatting.RESET + this.game.getInnocentsRemaining());
			
			lines.add(Formatting.YELLOW + "Bow Dropped: " + (!this.game.bows.isEmpty() ? Formatting.GREEN + "Yes" : Formatting.RED + "No"));
			
			lines.add(Formatting.RESET.toString());
			
			lines.add("Map: " + this.mapName);
			
			this.render(this.scoreboard, teamAndObjective.getRight(), lines.toArray(new String[0]));
		});
		
		int ticksTillStart = this.game.ticksTillStart;
		if (ticksTillStart > 0) {
			List<String> lines = new ArrayList<>(3);
			
			lines.add(Formatting.YELLOW + "Roles In: " + Formatting.RESET + this.formatTime(ticksTillStart));
			
			lines.add("");
			
			lines.add("Map: " + this.mapName);
			
			this.render(this.scoreboard, this.startingObjective, lines.toArray(new String[0]));
		}
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
		this.roleScoreboardMap.values().forEach(teamAndObjective -> {
			this.scoreboard.removeTeam(teamAndObjective.getLeft());
			this.scoreboard.removeObjective(teamAndObjective.getRight());
		});
		this.scoreboard.removeObjective(this.startingObjective);
	}
}