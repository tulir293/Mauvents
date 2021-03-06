package net.maunium.bukkit.Mauvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import net.maunium.bukkit.Maussentials.Utils.IngameCommandExecutor;
import net.maunium.bukkit.Maussentials.Utils.MetadataUtils;
import net.maunium.bukkit.Maussentials.Utils.SerializableLocation;
import net.maunium.bukkit.Maussentials.Utils.DelayedActions.DelayedAction;

public class LTS implements Listener, IngameCommandExecutor {
	public static final String IN_LTS = "MauventsLTSInEvent";
	private Mauvents plugin;
	private Location lobby = null, team1 = null, team2 = null;
	private int minPlayers = 6;
	private Set<UUID> players = new HashSet<UUID>();
	private ItemStack[] armor, inventory;
	private boolean respawnGreen = false, respawnRed = false, started = false;
	private ScoreboardManager sbm;
	
	public LTS(Mauvents plugin) {
		this.plugin = plugin;
		sbm = plugin.getServer().getScoreboardManager();
		this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
		this.plugin.getCommand("maulastteamstanding").setExecutor(this);
		if (plugin.getConfig().contains("lts.lobby")) lobby = SerializableLocation.fromString(plugin.getConfig().getString("lts.lobby")).toLocation();
		if (plugin.getConfig().contains("lts.team1")) team1 = SerializableLocation.fromString(plugin.getConfig().getString("lts.team1")).toLocation();
		if (plugin.getConfig().contains("lts.team2")) team2 = SerializableLocation.fromString(plugin.getConfig().getString("lts.team2")).toLocation();
		minPlayers = plugin.getConfig().getInt("lts.min-players", 4);
		
		armor = new ItemStack[] { new ItemStack(Material.IRON_BOOTS), new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_CHESTPLATE),
				new ItemStack(Material.IRON_HELMET) };
		
		inventory = new ItemStack[36];
		Arrays.fill(inventory, new ItemStack(Material.MUSHROOM_SOUP));
		ItemStack is = new ItemStack(Material.DIAMOND_SWORD);
		is.addEnchantment(Enchantment.DAMAGE_ALL, 1);
		inventory[0] = is;
		
		Team red = sbm.getMainScoreboard().getTeam("red");
		Team green = sbm.getMainScoreboard().getTeam("green");
		if (red == null) red = sbm.getMainScoreboard().registerNewTeam("red");
		if (green == null) green = sbm.getMainScoreboard().registerNewTeam("green");
		red.setAllowFriendlyFire(false);
		red.setCanSeeFriendlyInvisibles(true);
		red.setDisplayName("Red");
		red.setPrefix(ChatColor.RED.toString());
		red.setSuffix(ChatColor.RESET.toString());
		
		green.setAllowFriendlyFire(false);
		green.setCanSeeFriendlyInvisibles(true);
		green.setDisplayName("Green");
		green.setPrefix(ChatColor.GREEN.toString());
		green.setSuffix(ChatColor.RESET.toString());
	}
	
	public boolean hasStarted() {
		return started;
	}
	
	public boolean enoughPlayers() {
		return players.size() >= minPlayers;
	}
	
	public boolean isSetUp() {
		return team1 != null && team2 != null && lobby != null && minPlayers > 1;
	}
	
	public void setTeam1(Location l) {
		team1 = l;
		plugin.getConfig().set("lts.team1", new SerializableLocation(l).toString());
	}
	
	public void setTeam2(Location l) {
		team2 = l;
		plugin.getConfig().set("lts.team2", new SerializableLocation(l).toString());
	}
	
	public void setLobby(Location l) {
		lobby = l;
		plugin.getConfig().set("lts.lobby", new SerializableLocation(l).toString());
	}
	
	public void start() {
		boolean flipswitch = true;
		List<UUID> shuffled = new ArrayList<UUID>(players);
		Collections.shuffle(shuffled);
		for (UUID u : shuffled) {
			Player p = plugin.getServer().getPlayer(u);
			if (p != null && p.isOnline()) {
				p.setScoreboard(sbm.getMainScoreboard());
				p.getInventory().setArmorContents(armor);
				p.getInventory().setContents(inventory);
				if (flipswitch) {
					p.teleport(team1);
					sbm.getMainScoreboard().getTeam("green").addPlayer(p);
				} else {
					p.teleport(team2);
					sbm.getMainScoreboard().getTeam("red").addPlayer(p);
				}
			} else players.remove(u);
			flipswitch = !flipswitch;
		}
		started = true;
	}
	
	public void end() {
		Team red = sbm.getMainScoreboard().getTeam("red"), green = sbm.getMainScoreboard().getTeam("green");
		if (red.getPlayers().size() != 0 && green.getPlayers().size() != 0) return;
		else if (red.getPlayers().size() == 0 && green.getPlayers().size() == 0) plugin.getServer().broadcastMessage(plugin.translateStd("lts.win.null"));
		else if (red.getPlayers().size() == 0) {
			plugin.getServer().broadcastMessage(plugin.translateStd("lts.win.green"));
			for (OfflinePlayer p : green.getPlayers()) {
				green.removePlayer(p);
				players.remove(p.getUniqueId());
				if (p.isOnline()) {
					Player pp = (Player) p;
					MetadataUtils.removeMetadata(pp, IN_LTS, plugin);
					pp.teleport(plugin.getServer().getWorlds().get(0).getSpawnLocation());
				}
			}
		} else if (green.getPlayers().size() == 0) {
			plugin.getServer().broadcastMessage(plugin.translateStd("lts.win.red"));
			for (OfflinePlayer p : red.getPlayers()) {
				red.removePlayer(p);
				players.remove(p.getUniqueId());
				if (p.isOnline()) {
					Player pp = (Player) p;
					MetadataUtils.removeMetadata(pp, IN_LTS, plugin);
					pp.teleport(plugin.getServer().getWorlds().get(0).getSpawnLocation());
				}
			}
		}
		
		for (UUID u : players) {
			players.remove(u);
			Player p = plugin.getServer().getPlayer(u);
			if (p != null) {
				MetadataUtils.removeMetadata(p, IN_LTS, plugin);
				p.teleport(plugin.getServer().getWorlds().get(0).getSpawnLocation());
			}
		}
		
		players.clear();
		started = false;
	}
	
	public boolean join(Player p) {
		if (started || players.contains(p.getUniqueId())) return false;
		else {
			new DelayedAction(p, 100, new Runnable() {
				// Success runnable
				@Override
				public void run() {
					players.add(p.getUniqueId());
					MetadataUtils.setFixedMetadata(p, IN_LTS, true, plugin);
					p.teleport(lobby);
					p.getInventory().clear();
					p.getInventory().setArmorContents(new ItemStack[p.getInventory().getArmorContents().length]);
					p.sendMessage(plugin.translateStd("lts.lobby.tped"));
				}
			}, new Runnable() {
				// Fail runnable
				@Override
				public void run() {
					p.sendMessage(plugin.translateErr("lts.lobby.tpfail"));
				}
			}, 15, 0).start();;
			p.sendMessage(plugin.translateStd("lts.lobby.tping"));
			return true;
		}
	}
	
	public boolean leave(Player p, boolean death) {
		Team red = sbm.getMainScoreboard().getTeam("red"), green = sbm.getMainScoreboard().getTeam("green");
		if (!players.contains(p.getUniqueId()) && !p.hasMetadata(IN_LTS)) return false;
		MetadataUtils.removeMetadata(p, IN_LTS, plugin);
		players.remove(p.getUniqueId());
		p.getInventory().clear();
		p.getInventory().setArmorContents(new ItemStack[p.getInventory().getArmorContents().length]);
		if (green.hasPlayer(p)) green.removePlayer(p);
		if (red.hasPlayer(p)) red.removePlayer(p);
		if (!death) p.teleport(plugin.getServer().getWorlds().get(0).getSpawnLocation());
		if (started && (green.getPlayers().size() == 0 || red.getPlayers().size() == 0)) end();
		return true;
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent evt) {
		if (evt.getPlayer().hasMetadata(IN_LTS) || players.contains(evt.getPlayer().getUniqueId())) leave(evt.getPlayer(), false);
	}
	
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent evt) {
		if (evt.getEntity().hasMetadata(IN_LTS) || players.contains(evt.getEntity().getUniqueId())) {
			// TODO: Respawning if uneven teams at start
			leave(evt.getEntity(), true);
		}
	}
	
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPreCommand(PlayerCommandPreprocessEvent evt) {
		if (evt.getPlayer().hasMetadata(IN_LTS) && players.contains(evt.getPlayer())) {
			if (!evt.getMessage().startsWith("/lts") && !evt.getMessage().startsWith("/maults") && !evt.getMessage().startsWith("/maulastteamstanding")
					&& !evt.getMessage().startsWith("/lastteamstanding") && !evt.getMessage().startsWith("/mauvents")) {
				evt.setCancelled(true);
				evt.setMessage("/thiscommandshallnotdoanythingthatwouldhelptheplayer");
				evt.getPlayer().sendMessage(plugin.translateErr("lts.commandinmatch"));
			}
		}
	}
	
	@Override
	public boolean onCommand(Player sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			if (!players.contains(sender.getUniqueId())) {
				if (!join(sender)) sender.sendMessage(plugin.translateErr("lts.alreadystarted"));
			} else {
				if (leave(sender, false)) sender.sendMessage(plugin.translateStd("lts.leave"));
				else sender.sendMessage(plugin.translateErr("lts.notin"));
			}
		} else {
			if (args[0].equalsIgnoreCase("join")) {
				if (!players.contains(sender.getUniqueId())) {
					if (!join(sender)) sender.sendMessage(plugin.translateErr("lts.alreadystarted"));
				} else sender.sendMessage(plugin.translateErr("lts.alreadyin"));
			} else if (args[0].equalsIgnoreCase("leave")) {
				if (players.contains(sender.getUniqueId())) {
					if (leave(sender, false)) sender.sendMessage(plugin.translateStd("lts.leave"));
					else sender.sendMessage(plugin.translateErr("lts.notin"));
				} else sender.sendMessage(plugin.translateErr("lts.notin"));
			}
		}
		return true;
	}
}