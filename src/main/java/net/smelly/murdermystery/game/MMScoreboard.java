package net.smelly.murdermystery.game;

import com.google.common.collect.Maps;
import net.minecraft.scoreboard.AbstractTeam.VisibilityRule;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.smelly.murdermystery.game.MMActive.Role;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.common.widget.SidebarWidget;

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

	public MMScoreboard(ServerWorld world, MMActive game, GlobalWidgets widgets) {
		this.game = game;
		this.world = world;
		this.mapTranslation = game.config.mapConfig().name();
		this.sidebar = widgets.addSidebar(
				Text.translatable("game.murder_mystery.murder_mystery").formatted(Formatting.GOLD, Formatting.BOLD)
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
		if(!this.game.isGameClosing() && this.world.getTime() % 10 == 0) this.updateRendering();
	}

	public void updateRendering() {
		int ticksTillStart = this.game.ticksTillStart;

		this.sidebar.set(content -> {
			if(ticksTillStart > 0) {
				content.add(Text.translatable("text.murder_mystery.sidebar.starting_in", Text.literal(this.formatTime(ticksTillStart)).formatted(Formatting.WHITE)).formatted(Formatting.YELLOW));
			}
			else {
				content.add(Text.translatable("text.murder_mystery.sidebar.time_left", Text.literal(this.formatTime(this.game.getTimeRemaining())).formatted(Formatting.WHITE)).formatted(Formatting.RED));
				content.add(Text.translatable("text.murder_mystery.sidebar.innocents_left", Text.literal(this.game.getInnocentsRemaining()).formatted(Formatting.WHITE)).formatted(Formatting.GREEN));

				content.add(Text.literal(""));

				content.add(Text.translatable("text.murder_mystery.sidebar.bow_dropped", (!this.game.bows.isEmpty() ? Text.translatable("gui.yes").formatted(Formatting.GREEN) : Text.translatable("gui.no").formatted(Formatting.RED))).formatted(Formatting.YELLOW));
			}
			content.add(Text.literal(""));

			content.add(Text.translatable("text.murder_mystery.sidebar.time_left", Text.literal(this.formatTime(this.game.getTimeRemaining())).formatted(Formatting.WHITE)).formatted(Formatting.RED));
			content.add(Text.translatable("text.murder_mystery.sidebar.map", Text.translatable(this.mapTranslation).formatted(Formatting.LIGHT_PURPLE)).formatted(Formatting.WHITE));
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
