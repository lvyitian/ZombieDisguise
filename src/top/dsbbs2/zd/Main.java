package top.dsbbs2.zd;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLib;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class Main extends JavaPlugin implements Listener {
    public Map<UUID, Zombie> zombies=new HashMap<>();
    public Set<Zombie> ignore=new HashSet<>();
    @Override
    public void onEnable()
    {
        Bukkit.getPluginManager().registerEvents(this,this);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if(PacketType.Play.Server.SPAWN_ENTITY.equals(event.getPacket().getType())) {
                    if(zombies.containsKey(event.getPlayer().getUniqueId())) {
                        if(zombies.get(event.getPlayer().getUniqueId()).getUniqueId().equals(event.getPacket().getUUIDs().getValues().get(0))) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        });
        Bukkit.getScheduler().runTaskTimer(this,()->
            zombies.forEach((k,v)->{
                Player p=Bukkit.getPlayer(k);
                v.setCustomName(p.getName());
                v.setCustomNameVisible(true);
                if(!p.isDead())
                {
                    p.setVelocity(v.getVelocity());
                    v.setSilent(p.isSilent());
                    v.setInvulnerable(p.isInvulnerable());
                    v.getActivePotionEffects().stream().map(PotionEffect::getType).forEach(v::removePotionEffect);
                    v.addPotionEffects(p.getActivePotionEffects());
                    p.setFireTicks(v.getFireTicks());
                    v.setMaxHealth(p.getMaxHealth());
                    v.setHealth(p.getHealth());
                    v.getEquipment().setArmorContents(p.getEquipment().getArmorContents());
                }
                v.teleport(p);
                v.setTarget(null);
            })
        ,0,1);
    }
    @Override
    public void onDisable()
    {
        zombies.values().forEach(Zombie::remove);
        zombies.clear();
        ignore.clear();
        zombies=null;
        ignore=null;
    }
    public void hidePlayer(Player p)
    {
        Bukkit.getOnlinePlayers().stream().filter(i->!Objects.equals(i,p)).forEach(i->i.hidePlayer(this,p));
    }
    public void disguise(Player p)
    {
        Zombie z=(Zombie)p.getWorld().spawnEntity(p.getLocation(), EntityType.ZOMBIE);
        z.setBaby(false);
        z.setSilent(p.isSilent());
        z.getEquipment().setArmorContents(p.getEquipment().getArmorContents());
        z.addPotionEffects(p.getActivePotionEffects());
        z.setInvulnerable(p.isInvulnerable());
        z.setMaxHealth(p.getMaxHealth());
        z.setHealth(p.getHealth());
        z.setFireTicks(p.getFireTicks());
        z.setCustomName(p.getName());
        z.setCustomNameVisible(true);
        zombies.put(p.getUniqueId(),z);
        hidePlayer(p);
    }
    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent e)
    {
        Zombie z=zombies.get(e.getPlayer().getUniqueId());
        if(z!=null)
        {
            z.remove();
            zombies.remove(e.getPlayer().getUniqueId());
        }
    }
    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void onPlayerArmor(PlayerArmorChangeEvent e)
    {
        Bukkit.getScheduler().runTask(this,()->Optional.ofNullable(zombies.get(e.getPlayer().getUniqueId())).ifPresent(i->i.getEquipment().setArmorContents(e.getPlayer().getEquipment().getArmorContents())));
    }
    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e)
    {
        Zombie z=zombies.get(e.getEntity().getUniqueId());
        if(z!=null)
        {
            z.setAI(false);
            z.setSilent(true);
            z.setInvulnerable(true);
            z.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,99999999,255,true,false),true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e)
    {
        if(zombies.containsValue(e.getEntity()))
        {
            e.setCancelled(true);
            zombies.entrySet().stream().filter(i->Objects.equals(i.getValue(),e.getEntity())).findFirst().map(Map.Entry::getKey).map(Bukkit::getPlayer).ifPresent(i->i.setHealth(0));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent e)
    {
        Bukkit.getScheduler().runTask(this,()->hidePlayer(e.getPlayer()));
        zombies.get(e.getPlayer().getUniqueId()).setAI(true);
    }
    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent e)
    {
        if(zombies.containsValue(e.getEntity()))
            e.setTarget(null);
    }
    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e)
    {
        if(ignore.remove(e.getEntity())||e.getCause().name().contains("ENTITY"))
        {
            return;
        }
        if(zombies.containsValue(e.getEntity()))
        {
            zombies.entrySet().stream().filter(i->Objects.equals(i.getValue(),e.getEntity())).findFirst().map(Map.Entry::getKey).map(Bukkit::getPlayer).ifPresent(i->i.damage(e.getFinalDamage()));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e)
    {
        if(zombies.containsValue(e.getEntity()))
        {
            Zombie z=(Zombie)e.getEntity();
            ignore.add(z);
            Entity damager=e.getDamager();
            if (damager instanceof Projectile)
            {
                Projectile pro=(Projectile) damager;
                if(pro.getShooter() instanceof Entity)
                  damager=(Entity) pro.getShooter();
            }
            Entity damager2=damager;
            zombies.entrySet().stream().filter(i->Objects.equals(i.getValue(),e.getEntity())).findFirst().map(Map.Entry::getKey).map(Bukkit::getPlayer).ifPresent(i->i.damage(e.getFinalDamage(),damager2));
        }
    }
    public static Player proxiedToPlayer(ProxiedCommandSender c)
    {
        if(c.getCallee() instanceof Player)
            return (Player)c.getCallee();
        else if(c.getCallee() instanceof ProxiedCommandSender)
            return proxiedToPlayer((ProxiedCommandSender) c.getCallee());
        else return null;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(command.getName().equalsIgnoreCase("zd"))
        {
            Player p=null;
            if(sender instanceof Player)
            {
                p=(Player)sender;
            }
            if(sender instanceof ProxiedCommandSender)
                p=proxiedToPlayer((ProxiedCommandSender) sender);
            if(args.length>=1)
            {
                Player t=Bukkit.getPlayer(args[0]);
                if(t==null)
                {
                    sender.sendMessage("目标玩家不在线!");
                    return true;
                }
                p=t;
            }
            if(p==null)
            {
                sender.sendMessage("对于非玩家命令发送者，必须通过参数指定目标玩家!");
                return false;
            }
            disguise(p);
            return true;
        }
        return super.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if(args.length<=1)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(i->i.startsWith(args[args.length-1])).collect(Collectors.toList());
        return Arrays.asList();
    }
}
