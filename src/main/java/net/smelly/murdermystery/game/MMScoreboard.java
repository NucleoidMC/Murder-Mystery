package net.smelly.murdermystery.game;

import com.google.common.collect.Maps;
import net.minecraft.scoreboard.AbstractTeam.VisibilityRule;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.smelly.murdermystery.game.MMActive.Role;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;
import xyz.nucleoid.plasmid.widget.SidebarWidget;

import java.util.EnumMap;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMScoreboard implements AutoCloseable {
	private final MMActive game;
	private final ServerWorld world;
	private final String mapTranslation;
	private final ServerScoreboard scoreboard;
	private final SidebarWidget sidebar;
	private final EnumMap<Role, Team> roleTeamMap;
	
	public MMScoreboard(MMActive game, GlobalWidgets widgets) {
		this.game = game;
		this.world = game.gameSpace.getWorld();
		this.mapTranslation = game.config.mapConfig.name;
		this.sidebar = widgets.addSidebar(
			new TranslatableText("game.murder_mystery.murder_mystery").formatted(Formatting.GOLD, Formatting.BOLD)
		);
		this.scoreboard = this.world.getServer().getScoreboard();
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
		String name = role.toString();
		Team team = scoreboard.getTeam(name) != null ? scoreboard.getTeam(name) : scoreboard.addTeam(name);
		team.setNameTagVisibilityRule(VisibilityRule.NEVER);
		return team;
	}
	
	public void addPlayerToRole(ServerPlayerEntity player, Role team) {
		this.scoreboard.addPlayerToTeam(player.getEntityName(), this.roleTeamMap.get(team));
	}
	
	public void tick() {
		if (!this.game.isGameClosing() && this.world.getTime() % 10 == 0) this.updateRendering();
	}
	
	public void updateRendering() {
		int ticksTillStart = this.game.ticksTillStart;

		this.sidebar.set(content -> {
			if (ticksTillStart > 0) {
				content.writeFormattedTranslated(Formatting.YELLOW, "text.murder_mystery.sidebar.starting_in", new LiteralText(this.formatTime(ticksTillStart)).formatted(Formatting.WHITE));
				content.writeLine("");
			} else {
				content.writeFormattedTranslated(Formatting.RED, "text.murder_mystery.sidebar.time_left", new LiteralText(this.formatTime(this.game.getTimeRemaining())).formatted(Formatting.WHITE));
				content.writeFormattedTranslated(Formatting.GREEN, "text.murder_mystery.sidebar.innocents_left", new LiteralText(this.game.getInnocentsRemaining()).formatted(Formatting.WHITE));

				content.writeLine("");

				content.writeFormattedTranslated(Formatting.YELLOW, "text.murder_mystery.sidebar.bow_dropped", (!this.game.bows.isEmpty() ? new TranslatableText("gui.yes").formatted(Formatting.GREEN) : new TranslatableText("gui.no").formatted(Formatting.RED)));
				content.writeLine(Formatting.WHITE.toString());
			}

			content.writeFormattedTranslated(Formatting.RED, "text.murder_mystery.sidebar.time_left", new LiteralText(this.formatTime(this.game.getTimeRemaining())).formatted(Formatting.WHITE));
			content.writeTranslated("text.murder_mystery.sidebar.map", new TranslatableText(this.mapTranslation).formatted(Formatting.LIGHT_PURPLE));
		});
	}
	
	private String formatTime(long ticks) {
		return String.format("%02d:%02d", ticks / (20 * 60), (ticks / 20) % 60);
	}

	@Override
	public void close() {
		this.roleTeamMap.values().forEach(this.scoreboard::removeTeam);
		this.sidebar.close();
	}
}
