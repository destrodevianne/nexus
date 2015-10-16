package cz.nxs.events.engine.main.events;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javolution.text.TextBuilder;
import javolution.util.FastList;
import javolution.util.FastMap;
import javolution.util.FastSet;
import cz.nxs.events.Configurable;
import cz.nxs.events.EventGame;
import cz.nxs.events.NexusLoader;
import cz.nxs.events.engine.EventBuffer;
import cz.nxs.events.engine.EventConfig;
import cz.nxs.events.engine.EventManager;
import cz.nxs.events.engine.EventRewardSystem;
import cz.nxs.events.engine.EventWarnings;
import cz.nxs.events.engine.base.ConfigModel;
import cz.nxs.events.engine.base.Event;
import cz.nxs.events.engine.base.EventMap;
import cz.nxs.events.engine.base.EventPlayerData;
import cz.nxs.events.engine.base.EventSpawn;
import cz.nxs.events.engine.base.EventType;
import cz.nxs.events.engine.base.Loc;
import cz.nxs.events.engine.base.PvPEventPlayerData;
import cz.nxs.events.engine.base.RewardPosition;
import cz.nxs.events.engine.base.SpawnType;
import cz.nxs.events.engine.html.EventHtmlManager;
import cz.nxs.events.engine.lang.LanguageEngine;
import cz.nxs.events.engine.main.MainEventManager;
import cz.nxs.events.engine.main.base.MainEventInstanceType;
import cz.nxs.events.engine.stats.EventStatsManager;
import cz.nxs.events.engine.stats.GlobalStats;
import cz.nxs.events.engine.stats.GlobalStatsModel;
import cz.nxs.events.engine.team.EventTeam;
import cz.nxs.interf.PlayerEventInfo;
import cz.nxs.interf.callback.CallbackManager;
import cz.nxs.interf.delegate.CharacterData;
import cz.nxs.interf.delegate.FenceData;
import cz.nxs.interf.delegate.InstanceData;
import cz.nxs.interf.delegate.ItemData;
import cz.nxs.interf.delegate.NpcData;
import cz.nxs.interf.delegate.NpcTemplateData;
import cz.nxs.interf.delegate.PartyData;
import cz.nxs.interf.delegate.SkillData;
import cz.nxs.l2j.CallBack;
import cz.nxs.l2j.ClassType;

public abstract class AbstractMainEvent extends Event implements Configurable, EventGame
{
	protected MainEventManager _manager;
	protected Map<SpawnType, String> _spawnTypes;
	protected InstanceData[] _instances;
	protected int _runningInstances;
	protected String _htmlDescription = null;
	protected FastMap<Integer, FastMap<Integer, EventTeam>> _teams;
	protected Map<Integer, List<FenceData>> _fences;
	protected Map<Integer, List<NpcData>> _npcs;
	protected List<Integer> _rewardedInstances;
	protected String scorebarText;
	private FastMap<MainEventInstanceType, FastList<PlayerEventInfo>> _tempPlayers;
	private final FastList<String> _configCategories;
	protected final FastMap<String, ConfigModel> _configs;
	private final FastMap<String, ConfigModel> _mapConfigs;
	private final FastMap<String, ConfigModel> _instanceTypeConfigs;
	protected RewardPosition[] _rewardTypes = null;
	protected WaweRespawnScheduler _waweScheduler;
	protected List<PlayerEventInfo> _spectators;
	protected boolean _allowScoreBar;
	protected boolean _allowSchemeBuffer;
	protected boolean _removeBuffsOnEnd;
	protected boolean _allowSummons;
	protected boolean _allowPets;
	protected boolean _hideTitles;
	protected boolean _removePartiesOnStart;
	protected boolean _rejoinEventAfterDisconnect;
	protected boolean _removeWarningAfterReconnect;
	protected boolean _enableRadar;
	protected int _countOfShownTopPlayers;
	private int firstRegisteredRewardCount;
	private String firstRegisteredRewardType;
	private boolean _firstBlood;
	protected PlayerEventInfo _firstBloodPlayer;
	private int _partiesCount;
	private int _afkHalfReward;
	private int _afkNoReward;
	private final FastMap<Integer, MainEventInstanceType> _types;
	protected int[] notAllovedSkillls;
	protected int[] notAllovedItems;
	private String[] enabledTiers;
	private int[] setOffensiveSkills;
	private int[] setNotOffensiveSkills;
	private int[] setNeutralSkills;
	private List<PlayerEventInfo> _firstRegistered;
	
	public AbstractMainEvent(EventType type, MainEventManager manager)
	{
		super(type);
		
		_manager = manager;
		
		_teams = new FastMap<>();
		
		_rewardedInstances = new FastList<>();
		
		_spawnTypes = new FastMap<>();
		_spawnTypes.put(SpawnType.Regular, "Defines where the players will be spawned.");
		_spawnTypes.put(SpawnType.Buffer, "Defines where the buffer(s) will be spawned.");
		_spawnTypes.put(SpawnType.Fence, "Defines where fences will be spawned.");
		
		_configCategories = new FastList<>();
		_configs = new FastMap<>();
		_mapConfigs = new FastMap<>();
		_instanceTypeConfigs = new FastMap<>();
		
		loadConfigs();
		
		_types = new FastMap<>();
	}
	
	@Override
	public void loadConfigs()
	{
		addConfig(new ConfigModel("allowScreenScoreBar", "true", "True to allow the screen score bar, showing mostly scores for all teams and time left till the event ends.", ConfigModel.InputType.Boolean));
		if (!getEventType().isFFAEvent())
		{
			ConfigModel divideMethod = new ConfigModel("divideToTeamsMethod", "LevelOnly", "The method used to divide the players into the teams on start of the event. All following methods try to put similar count of healers to all teams.<br1><font color=LEVEL>LevelOnly</font> sorts players by their level and then divides them into the teams (eg. Player1 (lvl85) to teamA, Player2(level84) to teamB, Player3(lvl81) to teamA, Player4(lvl75) to teamB, Player5(lvl70) to teamA,...)<br1><font color=LEVEL>PvPsAndLevel</font>: in addition to sorting by level, this method's main sorting factor are player's PvP kills. The rest of dividing procedure is same as for LevelsOnly. Useful for PvP servers, where level doesn't matter much.<br1>", ConfigModel.InputType.Enum);
			
			divideMethod.addEnumOptions(new String[]
			{
				"LevelOnly",
				"PvPsAndLevel"
			});
			addConfig(divideMethod);
			
			addConfig(new ConfigModel("balanceHealersInTeams", "true", "Put true if you want the engine to try to balance the count of healers in all teams (in all teams same healers count), making it as similar as possible.", ConfigModel.InputType.Boolean));
		}
		else
		{
			addConfig(new ConfigModel("announcedTopPlayersCount", "5", "You can specify the count of top players, that will be announced (in chat) in the end of the event."));
		}
		addConfig(new ConfigModel("runTime", "20", "The run time of this event, launched automatically by the scheduler. Max value globally for all events is 120 minutes. In minutes!"));
		
		addConfig(new ConfigModel("minLvl", "-1", "Minimum level for players participating the event (playerLevel >= value)."));
		addConfig(new ConfigModel("maxLvl", "100", "Maximum level for players participating the event (playerLevel <= value)."));
		addConfig(new ConfigModel("minPlayers", "2", "The minimum count of players required to start one instance of the event."));
		addConfig(new ConfigModel("maxPlayers", "-1", "The maximum count of players possible to play in the event. Put -1 to make it unlimited."));
		addConfig(new ConfigModel("removeBufsOnEnd", "true", "Put true to make that the buffs are removed from all players when the event ends (or gets aborted).", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("removePartiesOnStart", "false", "Put true if you want that when the event starts, to automatically delete all parties, that had been created BEFORE the event started.", ConfigModel.InputType.Boolean));
		
		addConfig(new ConfigModel("rejoinAfterDisconnect", "true", "When a player is on event and disconnects from the server, this gives <font color=7f7f7f>(if set on true)</font> him the opportunity to get back to the event if he relogins. The engine will simply wait if he logins again, and then teleport him back to the event (to his previous team). Sometimes it can happen that, for example, the whole team disconnects and the event is aborted, so then the engine will not teleport the player back to the event.", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("removeWarningAfterRejoin", "true", "Works if <font color=LEVEL>rejoinAfterDisconnect = true</font>. When a player successfully re-joins his previous event after he disconnected from server and then logged in again, this feature will remove the warning point which he received when he disconnected. Remember that if a player has a configurable count of warnings (by default 3), he is unable to participate in any event. Warnings decrease by 1 every day.", ConfigModel.InputType.Boolean));
		
		addConfig(new ConfigModel("playersInInstance", "-1", "This config currently has no use ;)."));
		
		addConfig(new ConfigModel("allowPotions", "false", "Specify if you want to allow players using potions in the event.", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("allowSummons", "true", "Put false if you want to disable summons on this event.", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("allowPets", "true", "Put false if you want to disable pets on this event.", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("allowHealers", "true", "Put false if you want to permit healer classes to register to the event.", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("hideTitles", "false", "Put true to disable titles containing player's event stats.", ConfigModel.InputType.Boolean));
		
		addConfig(new ConfigModel("removeBuffsOnStart", "true", "If 'true', all buffs will be removed from players on first teleport to the event.", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("removeBuffsOnRespawn", "false", "If 'true', all buffs will be removed from players when they respawn. Useful for certain servers.", ConfigModel.InputType.Boolean));
		
		addConfig(new ConfigModel("notAllowedSkills", "", "Put here skills that won't be aviable for use in this event <font color=7f7f7f>(write one skill's ID and click Add; to remove the skill, simply click on it's ID in the list)</font>", ConfigModel.InputType.MultiAdd));
		addConfig(new ConfigModel("notAllowedItems", "", "Put here items that won't be aviable for use in this event <font color=7f7f7f>(write one skill's ID and click Add; to remove the skill, simply click on it's ID in the list)</font>", ConfigModel.InputType.MultiAdd));
		
		addConfig(new ConfigModel("enableRadar", "true", "Enable/disable the quest-like radar for players. It will show an arrow above player's head and point him to a RADAR type spawn of his team. Useful for example when you create a RADAR spawn right next to enemy team's flag (it will show all players from the one team where is the flag they need to capture). Works only if the active map contains a RADAR spawn (and spawn's teamID must be > 0).", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("dualboxCheck", "true", "You can enable/disable the registration dualbox check here.", ConfigModel.InputType.Boolean));
		addConfig(new ConfigModel("maxPlayersPerIp", "1", "If the 'dualboxCheck' config is enabled, you can specify here how many players with the same IP are allowed to be in the event."));
		
		addConfig(new ConfigModel("afkHalfReward", "120", "The time (in seconds) the player must be AFK to lower his reward (in the end of the event) by 50%. The AFK counter starts counting the time spent AFK after <font color=LEVEL>afkWarningDelay</font> + <font color=LEVEL>afkKickDelay</font> miliseconds (these two are Global configs) of idling (not clicking, not moving, not doing anything). Write 0 to disable this feature."));
		addConfig(new ConfigModel("afkNoReward", "300", "The time (in seconds) the player must be AFK to receive no reward in the end of the event.The AFK counter starts counting the time spent AFK after <font color=LEVEL>afkWarningDelay</font> + <font color=LEVEL>afkKickDelay</font> miliseconds (these two are Global configs) of idling (not clicking, not moving, not doing anything). Write 0 to disable this feature."));
		
		addConfig(new ConfigModel("firstRegisteredRewardCount", "10", "If you have specified a 'FirstRegisteredReward' reward, you can define here how many first registered players will be rewarded in the end of the event."));
		addConfig(new ConfigModel("firstRegisteredRewardType", "WinnersOnly", "Select here who will be rewarded with the 'FirstRegisteredReward' reward in the end of the event.", ConfigModel.InputType.Enum).addEnumOptions(new String[]
		{
			"WinnersOnly",
			"All"
		}));
		
		addConfig(new ConfigModel("countOfShownTopPlayers", "10", "Count of players shown in the Top-scorers list in the community board. Better not to use high values. If you don't want to use this feature (not recommended - ugly HTML), put 0."));
		
		addConfig(new ConfigModel("enabledTiers", "AllItems", "This config is not fully implemented. Requires gameserver support.", ConfigModel.InputType.MultiAdd));
		
		addInstanceTypeConfig(new ConfigModel("strenghtRate", "5", "Every instance has it's rate. This rate determines how 'strong' the players are inside. Strenght rate is used in some engine's calculations. Check out other configs. <font color=B46F6B>Values MUST be within 1-10. Setting it more causes problems.</font>"));
		
		addInstanceTypeConfig(new ConfigModel("minLvl", "-1", "Min level (for players) for this instance."));
		addInstanceTypeConfig(new ConfigModel("maxLvl", "100", "Max level (for players) for this instance."));
		
		addInstanceTypeConfig(new ConfigModel("minPvps", "0", "Min PvP points count to play in this instance."));
		addInstanceTypeConfig(new ConfigModel("maxPvps", "-1", "Max PvP points count to play in this instance. Put -1 to make it infinity."));
		
		addInstanceTypeConfig(new ConfigModel("minPlayers", "2", "Count of players required to start this instance. If there's less players, then the instance tries to divide it's players to stronger instances (check out config <font color=LEVEL>joinStrongerInstIfNeeded</font>) and if it doesn't success (the config is set to false or all possible stronger instances are full), it will unregister the players from the event. Check out other configs related to "));
		addInstanceTypeConfig(new ConfigModel("joinStrongerInstIfNeeded", "False", "If there are not enought players needed for this instance to start (as specified in <font color=LEVEL>minPlayers</font> config), the instance will try to divide it's players <font color=7f7f7f>(players, that CAN'T join any other instance - cuz they either don't meet their criteria or the instances are full already)</font> to stronger instances (if they aren't full yet; level, pvp, equip and other checks are not applied in this case).", ConfigModel.InputType.Boolean));
		addInstanceTypeConfig(new ConfigModel("joinStrongerInstMaxDiff", "2", "If <font color=LEVEL>joinStrongerInstIfNeeded</font> is enabled, this specifies the maximum allowed difference between strength rate of both instances (where <font color=ac9887>the weaker instance</font> with not enought players divides it's players to <font color=ac9887>a stronger instance</font>)."));
		
		addInstanceTypeConfig(new ConfigModel("maxPlayers", "-1", "Max players ammount aviable for this instance. Put -1 to make it infinity."));
	}
	
	public abstract void runEvent();
	
	public abstract void onEventEnd();
	
	public abstract void clearEvent(int paramInt);
	
	protected abstract boolean instanceEnded();
	
	protected abstract void endInstance(int paramInt, boolean paramBoolean1, boolean paramBoolean2, boolean paramBoolean3);
	
	protected abstract void respawnPlayer(PlayerEventInfo paramPlayerEventInfo, int paramInt);
	
	protected abstract String getScorebar(int paramInt);
	
	protected abstract String getTitle(PlayerEventInfo paramPlayerEventInfo);
	
	public abstract String getHtmlDescription();
	
	protected abstract AbstractEventInstance createEventInstance(InstanceData paramInstanceData);
	
	protected abstract AbstractEventInstance getMatch(int paramInt);
	
	protected abstract int initInstanceTeams(MainEventInstanceType paramMainEventInstanceType, int paramInt);
	
	protected abstract AbstractEventData createEventData(int paramInt);
	
	protected abstract AbstractEventData getEventData(int paramInt);
	
	protected abstract class AbstractEventInstance implements Runnable
	{
		protected InstanceData _instance;
		protected Clock _clock;
		protected boolean _canBeAborted = false;
		protected boolean _canRewardIfAborted = false;
		protected boolean _forceNotRewardThisInstance = false;
		protected ScheduledFuture<?> _task;
		
		public AbstractEventInstance(InstanceData instance)
		{
			_instance = instance;
			
			_clock = new Clock(this);
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: created abstracteventinstance for instanceId " + instance.getId());
			}
		}
		
		public abstract boolean isActive();
		
		public void setCanBeAborted()
		{
			_canBeAborted = true;
		}
		
		public void forceNotRewardThisInstance()
		{
			_forceNotRewardThisInstance = true;
			synchronized (_rewardedInstances)
			{
				_rewardedInstances.add(_instance.getId());
			}
		}
		
		public void setCanRewardIfAborted()
		{
			_canRewardIfAborted = true;
		}
		
		public InstanceData getInstance()
		{
			return _instance;
		}
		
		public AbstractMainEvent.Clock getClock()
		{
			return _clock;
		}
		
		public ScheduledFuture<?> scheduleNextTask(int time)
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: abstractmaininstance: scheduling next task in " + time);
			}
			if (_clock._task != null)
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: abstractmaininstane: _clock_task is not null");
				}
				_clock._task.cancel(false);
				_clock._task = null;
			}
			else if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: abstractmaininstance: _clock_task is NULL!");
			}
			if (time > 0)
			{
				_task = CallBack.getInstance().getOut().scheduleGeneral(this, time);
			}
			else
			{
				CallBack.getInstance().getOut().executeTask(this);
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: next task scheduled.");
			}
			return _task;
		}
		
		public void abort()
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: abstractmaininstance: aborting...");
			}
			if (_task != null)
			{
				_task.cancel(false);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: abstractmaininsance _task is not null");
				}
			}
			else if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: abstractmaininstance _task is NULL!");
			}
			_clock.abort();
		}
	}
	
	protected abstract class AbstractEventData
	{
		protected int _instanceId;
		
		protected AbstractEventData(int instance)
		{
			_instanceId = instance;
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: abstracteventdata created data for instanceId = " + instance);
			}
		}
	}
	
	public void startRegistration()
	{
		_tempPlayers = new FastMap<>();
		_firstRegistered = new FastList<>();
		
		firstRegisteredRewardCount = getInt("firstRegisteredRewardCount");
		firstRegisteredRewardType = getString("firstRegisteredRewardType");
		if (!getString("enabledTiers").equals(""))
		{
			String[] splits = getString("enabledTiers").split(",");
			enabledTiers = new String[splits.length];
			try
			{
				for (int i = 0; i < splits.length; i++)
				{
					enabledTiers[i] = splits[i];
				}
				Arrays.sort(enabledTiers);
			}
			catch (Exception e)
			{
				NexusLoader.debug("Error while loading config 'enabledTiers' for event " + getEventName() + " - " + e.toString(), Level.SEVERE);
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: startRegistration() done");
		}
	}
	
	public void initMap()
	{
		try
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: starting initMap()");
			}
			_fences = new FastMap<>();
			_npcs = new FastMap<>();
			
			EventMap map = _manager.getMap();
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: init map - " + map.getMapName());
			}
			for (InstanceData instance : _instances)
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: initmap iterating instance " + instance.getId());
				}
				_fences.put(Integer.valueOf(instance.getId()), new FastList<>());
				for (EventSpawn spawn : map.getSpawns(-1, SpawnType.Fence))
				{
					FenceData fence = CallBack.getInstance().getOut().createFence(2, spawn.getFenceWidth(), spawn.getFenceLength(), spawn.getLoc().getX(), spawn.getLoc().getY(), spawn.getLoc().getZ(), map.getGlobalId());
					(_fences.get(Integer.valueOf(instance.getId()))).add(fence);
				}
				CallBack.getInstance().getOut().spawnFences(_fences.get(Integer.valueOf(instance.getId())), instance.getId());
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: initmap iterating instance spawned fences");
				}
				_npcs.put(Integer.valueOf(instance.getId()), new FastList<>());
				for (EventSpawn spawn : map.getSpawns(-1, SpawnType.Npc))
				{
					if (spawn.getNpcId() != -1)
					{
						NpcData npc = new NpcTemplateData(spawn.getNpcId()).doSpawn(spawn.getLoc().getX(), spawn.getLoc().getY(), spawn.getLoc().getZ(), 1, instance.getId());
						(_npcs.get(Integer.valueOf(instance.getId()))).add(npc);
					}
				}
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: initmap iterating instance spawned npcs");
				}
				int mapGuardId = EventConfig.getInstance().getGlobalConfigInt("mapGuardNpcId");
				if (mapGuardId != -1)
				{
					for (EventSpawn spawn : map.getSpawns(-1, SpawnType.MapGuard))
					{
						NpcData npc = new NpcTemplateData(mapGuardId).doSpawn(spawn.getLoc().getX(), spawn.getLoc().getY(), spawn.getLoc().getZ(), 1, instance.getId());
						(_npcs.get(Integer.valueOf(instance.getId()))).add(npc);
					}
				}
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: initmap iterating instance spawned map guards");
				}
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: initmap finished");
			}
		}
		catch (NullPointerException e)
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: error on initMap() " + NexusLoader.getTraceString(e.getStackTrace()));
			}
			NexusLoader.debug("Error on initMap()", Level.WARNING);
			e.printStackTrace();
		}
	}
	
	public void cleanMap(int instanceId)
	{
		try
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: starting cleanmap(), instanceId " + instanceId);
			}
			if (_instances != null)
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: instances are not null");
				}
				for (InstanceData instance : _instances)
				{
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: iterating instance " + instance.getId());
					}
					if ((instanceId == 0) || (instance.getId() == instanceId))
					{
						if ((_fences != null) && (_fences.containsKey(Integer.valueOf(instance.getId()))))
						{
							CallBack.getInstance().getOut().unspawnFences(_fences.get(Integer.valueOf(instance.getId())));
						}
						if (NexusLoader.detailedDebug)
						{
							print("AbstractMainEvent: instance + " + instance.getId() + ", fences deleted");
						}
						if ((_npcs != null) && (_npcs.containsKey(instance.getId())))
						{
							for (NpcData npc : _npcs.get(instance.getId()))
							{
								npc.deleteMe();
							}
						}
						if (NexusLoader.detailedDebug)
						{
							print("AbstractMainEvent: instance + " + instance.getId() + ", npcs deleted");
						}
						if (_fences != null)
						{
							_fences.remove(Integer.valueOf(instance.getId()));
						}
						if (_npcs != null)
						{
							_npcs.remove(Integer.valueOf(instance.getId()));
						}
						if (NexusLoader.detailedDebug)
						{
							print("AbstractMainEvent: instance + " + instance.getId() + " cleaned.");
						}
					}
				}
			}
			if (instanceId == 0)
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: set npcs and fences to null (instanceId = 0)");
				}
				_npcs = null;
				_fences = null;
			}
			else if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: cannot set npcs and fences to null yet, instanceId != 0");
			}
		}
		catch (NullPointerException e)
		{
			e.printStackTrace();
		}
	}
	
	public void initEvent()
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: initEvent starting");
		}
		if (_rewardTypes == null)
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: event " + getEventName() + " has not set up any _rewardTypes");
			}
			debug("Event " + getEventName() + " has not set up _rewardTypes. You've propably forgotten to call 'setRewardTypes()' in event's constructor.");
		}
		_firstBlood = false;
		
		_spectators = new FastList<>();
		if (!_rewardedInstances.isEmpty())
		{
			_rewardedInstances.clear();
		}
		Collections.sort(_manager.getMap().getSpawns(), EventMap.compareByIdAsc);
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: spawns sorted, instances cleaned");
		}
		_afkHalfReward = getInt("afkHalfReward");
		_afkNoReward = getInt("afkNoReward");
		
		_allowScoreBar = getBoolean("allowScreenScoreBar");
		_allowSchemeBuffer = EventConfig.getInstance().getGlobalConfigBoolean("eventSchemeBuffer");
		_removeBuffsOnEnd = getBoolean("removeBufsOnEnd");
		_allowSummons = getBoolean("allowSummons");
		_allowPets = getBoolean("allowPets");
		_hideTitles = getBoolean("hideTitles");
		_removePartiesOnStart = getBoolean("removePartiesOnStart");
		_countOfShownTopPlayers = getInt("countOfShownTopPlayers");
		
		_rejoinEventAfterDisconnect = getBoolean("rejoinAfterDisconnect");
		_removeWarningAfterReconnect = getBoolean("removeWarningAfterRejoin");
		
		_enableRadar = getBoolean("enableRadar");
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: scorebar - " + _allowScoreBar + ", scheme buffer = " + _allowSchemeBuffer);
		}
		if (!getString("notAllowedItems").equals(""))
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: loading not allowed items");
			}
			String[] splits = getString("notAllowedItems").split(",");
			notAllovedItems = new int[splits.length];
			try
			{
				for (int i = 0; i < splits.length; i++)
				{
					notAllovedItems[i] = Integer.parseInt(splits[i]);
				}
				Arrays.sort(notAllovedItems);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: not allowed items = " + notAllovedItems.toString());
				}
			}
			catch (Exception e)
			{
				NexusLoader.debug("Error while loading config 'notAllowedItems' for event " + getEventName() + " - " + e.toString(), Level.SEVERE);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: error while loading not allowed items " + NexusLoader.getTraceString(e.getStackTrace()));
				}
			}
		}
		else if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: no not allowed items specified!");
		}
		if (!getString("notAllowedSkills").equals(""))
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: loading not allowed skills!");
			}
			String[] splits = getString("notAllowedSkills").split(",");
			notAllovedSkillls = new int[splits.length];
			try
			{
				for (int i = 0; i < splits.length; i++)
				{
					notAllovedSkillls[i] = Integer.parseInt(splits[i]);
				}
				Arrays.sort(notAllovedSkillls);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: not allowed skills = " + notAllovedSkillls.toString());
				}
			}
			catch (Exception e)
			{
				NexusLoader.debug("Error while loading config 'notAllowedSkills' for event " + getEventName() + " - " + e.toString(), Level.SEVERE);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: error while loading not allowed skills " + NexusLoader.getTraceString(e.getStackTrace()));
				}
			}
		}
		else if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: no not allowed skills specified!");
		}
		loadOverridenSkillsParameters();
		
		_partiesCount = 0;
		_firstBloodPlayer = null;
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: initEvent finished for AbstractMainEvent()");
		}
		dumpConfigs();
	}
	
	private void loadOverridenSkillsParameters()
	{
		String s = EventConfig.getInstance().getGlobalConfigValue("setOffensiveSkills");
		if ((s != null) && (s.length() > 0))
		{
			try
			{
				String[] splits = s.split(";");
				setOffensiveSkills = new int[splits.length];
				try
				{
					for (int i = 0; i < splits.length; i++)
					{
						setOffensiveSkills[i] = Integer.parseInt(splits[i]);
					}
					Arrays.sort(setOffensiveSkills);
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: set offensive skills = " + setOffensiveSkills.toString());
					}
				}
				catch (Exception e)
				{
					NexusLoader.debug("Error while loading GLOBAL config 'setOffensiveSkills' in event " + getEventName() + " - " + e.toString(), Level.SEVERE);
				}
			}
			catch (Exception e)
			{
				NexusLoader.debug("Error while loading GLOBAL config 'setOffensiveSkills' in event " + getEventName() + " - " + e.toString(), Level.SEVERE);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: error while loading 'setOffensiveSkills' GLOBAL config " + NexusLoader.getTraceString(e.getStackTrace()));
				}
			}
		}
		s = EventConfig.getInstance().getGlobalConfigValue("setNotOffensiveSkills");
		if ((s != null) && (s.length() > 0))
		{
			try
			{
				String[] splits = s.split(";");
				setNotOffensiveSkills = new int[splits.length];
				try
				{
					for (int i = 0; i < splits.length; i++)
					{
						setNotOffensiveSkills[i] = Integer.parseInt(splits[i]);
					}
					Arrays.sort(setNotOffensiveSkills);
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: set not offensive skills = " + setNotOffensiveSkills.toString());
					}
				}
				catch (Exception e)
				{
					NexusLoader.debug("Error while loading GLOBAL config 'setNotOffensiveSkills' in event " + getEventName() + " - " + e.toString(), Level.SEVERE);
				}
			}
			catch (Exception e)
			{
				NexusLoader.debug("Error while loading GLOBAL config 'setNotOffensiveSkills' in event " + getEventName() + " - " + e.toString(), Level.SEVERE);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: error while loading 'setNotOffensiveSkills' GLOBAL config " + NexusLoader.getTraceString(e.getStackTrace()));
				}
			}
		}
		s = EventConfig.getInstance().getGlobalConfigValue("setNeutralSkills");
		if ((s != null) && (s.length() > 0))
		{
			try
			{
				String[] splits = s.split(";");
				setNeutralSkills = new int[splits.length];
				try
				{
					for (int i = 0; i < splits.length; i++)
					{
						setNeutralSkills[i] = Integer.parseInt(splits[i]);
					}
					Arrays.sort(setNeutralSkills);
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: set neutral skills = " + setNeutralSkills.toString());
					}
				}
				catch (Exception e)
				{
					NexusLoader.debug("Error while loading GLOBAL config 'setNeutralSkills' in event " + getEventName() + " - " + e.toString(), Level.SEVERE);
				}
			}
			catch (Exception e)
			{
				NexusLoader.debug("Error while loading GLOBAL config 'setNeutralSkills' in event " + getEventName() + " - " + e.toString(), Level.SEVERE);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: error while loading 'setNeutralSkills' GLOBAL config " + NexusLoader.getTraceString(e.getStackTrace()));
				}
			}
		}
	}
	
	@Override
	public int isSkillOffensive(SkillData skill)
	{
		if ((setOffensiveSkills != null) && (Arrays.binarySearch(setOffensiveSkills, skill.getId()) >= 0))
		{
			return 1;
		}
		if ((setNotOffensiveSkills != null) && (Arrays.binarySearch(setNotOffensiveSkills, skill.getId()) >= 0))
		{
			return 0;
		}
		return -1;
	}
	
	@Override
	public boolean isSkillNeutral(SkillData skill)
	{
		if ((setNeutralSkills != null) && (Arrays.binarySearch(setNeutralSkills, skill.getId()) >= 0))
		{
			return true;
		}
		return false;
	}
	
	private void dumpConfigs()
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: dumping configs START =====================");
		}
		for (Map.Entry<String, ConfigModel> e : _configs.entrySet())
		{
			if (NexusLoader.detailedDebug)
			{
				print(e.getKey() + " - " + e.getValue().getValue());
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: dumping configs END ====================");
		}
	}
	
	protected void createTeams(int count, int instanceId)
	{
		try
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: creating " + count + " teams for instanceId " + instanceId);
			}
			switch (count)
			{
				case 1:
					createNewTeam(instanceId, 1, LanguageEngine.getMsg("team_ffaevent"));
					break;
				case 2:
					createNewTeam(instanceId, 1, LanguageEngine.getMsg("team_blue"), LanguageEngine.getMsg("team_fullname_blue"));
					createNewTeam(instanceId, 2, LanguageEngine.getMsg("team_red"), LanguageEngine.getMsg("team_fullname_red"));
					break;
				case 3:
					createNewTeam(instanceId, 1, LanguageEngine.getMsg("team_blue"), LanguageEngine.getMsg("team_fullname_blue"));
					createNewTeam(instanceId, 2, LanguageEngine.getMsg("team_red"), LanguageEngine.getMsg("team_fullname_red"));
					createNewTeam(instanceId, 3, LanguageEngine.getMsg("team_green"), LanguageEngine.getMsg("team_fullname_green"));
					break;
				case 4:
					createNewTeam(instanceId, 1, LanguageEngine.getMsg("team_blue"), LanguageEngine.getMsg("team_fullname_blue"));
					createNewTeam(instanceId, 2, LanguageEngine.getMsg("team_red"), LanguageEngine.getMsg("team_fullname_red"));
					createNewTeam(instanceId, 3, LanguageEngine.getMsg("team_green"), LanguageEngine.getMsg("team_fullname_green"));
					createNewTeam(instanceId, 4, LanguageEngine.getMsg("team_purple"), LanguageEngine.getMsg("team_fullname_purple"));
					break;
				case 5:
					createNewTeam(instanceId, 1, LanguageEngine.getMsg("team_blue"), LanguageEngine.getMsg("team_fullname_blue"));
					createNewTeam(instanceId, 2, LanguageEngine.getMsg("team_red"), LanguageEngine.getMsg("team_fullname_red"));
					createNewTeam(instanceId, 3, LanguageEngine.getMsg("team_green"), LanguageEngine.getMsg("team_fullname_green"));
					createNewTeam(instanceId, 4, LanguageEngine.getMsg("team_purple"), LanguageEngine.getMsg("team_fullname_purple"));
					createNewTeam(instanceId, 5, LanguageEngine.getMsg("team_yellow"), LanguageEngine.getMsg("team_fullname_yellow"));
					break;
				default:
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: the teams count is too high on event " + getEventName());
					}
					NexusLoader.debug("The TEAMS COUNT is too high for event " + getEventName() + " - max value is 5!! The event will start with 5 teams.", Level.WARNING);
					createTeams(5, instanceId);
					return;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	protected void createNewTeam(int instanceId, int id, String name, String fullName)
	{
		(_teams.get(Integer.valueOf(instanceId))).put(Integer.valueOf(id), new EventTeam(id, name, fullName));
		if (NexusLoader.detailedDebug)
		{
			print("... AbstractMainEvent: created new team for instanceId " + instanceId + ", id " + id + ", fullname " + fullName);
		}
	}
	
	protected void createNewTeam(int instanceId, int id, String name)
	{
		(_teams.get(Integer.valueOf(instanceId))).put(Integer.valueOf(id), new EventTeam(id, name));
		if (NexusLoader.detailedDebug)
		{
			print("... AbstractMainEvent: created new team for instanceId " + instanceId + ", id " + id);
		}
	}
	
	public boolean canRegister(PlayerEventInfo player)
	{
		if (!getBoolean("allowHealers"))
		{
			if (player.isPriest())
			{
				player.sendMessage("Healers are not allowed on the event.");
				return false;
			}
		}
		if (!checkItems(player))
		{
			player.sendMessage("Come back after you store the disallowed items to your warehouse.");
			return false;
		}
		int maxPlayers = getInt("maxPlayers");
		if ((maxPlayers != -1) && (maxPlayers >= _manager.getPlayersCount()))
		{
			if (NexusLoader.detailedDebug)
			{
				print("... registerPlayer() in AbstractMainEvent (canRegister()) for " + player.getPlayersName() + ", the event is full already! " + maxPlayers + "/" + _manager.getPlayersCount());
			}
			player.sendMessage(LanguageEngine.getMsg("registering_full"));
			return false;
		}
		synchronized (_tempPlayers)
		{
			for (MainEventInstanceType instance : _types.values())
			{
				if (canJoinInstance(player, instance))
				{
					if (NexusLoader.detailedDebug)
					{
						print("... registerPlayer() in AbstractMainEvent (canRegister()) for " + player.getPlayersName() + " player CAN join instancetype " + instance.getId());
					}
					if (!_tempPlayers.containsKey(instance))
					{
						_tempPlayers.put(instance, new FastList<>());
					}
					else
					{
						int max = instance.getConfigInt("maxPlayers");
						if ((max > -1) && ((_tempPlayers.get(instance)).size() >= max))
						{
							if (!NexusLoader.detailedDebug)
							{
								continue;
							}
							print("... registerPlayer() in AbstractMainEvent (canRegister()) for " + player.getPlayersName() + " instance type " + instance.getId() + " is full already (max " + max + ")");
							continue;
						}
					}
					(_tempPlayers.get(instance)).add(player);
					if (NexusLoader.detailedDebug)
					{
						print("... registerPlayer() in AbstractMainEvent (canRegister()) for " + player.getPlayersName() + " registered to instance type " + instance.getId());
					}
					if (_firstRegistered.size() < firstRegisteredRewardCount)
					{
						_firstRegistered.add(player);
						if (firstRegisteredRewardType.equals("WinnersOnly"))
						{
							player.sendMessage(LanguageEngine.getMsg("registered_first_type1", new Object[]
							{
								Integer.valueOf(firstRegisteredRewardCount)
							}));
						}
						else
						{
							player.sendMessage(LanguageEngine.getMsg("registered_first_type2", new Object[]
							{
								Integer.valueOf(firstRegisteredRewardCount)
							}));
						}
					}
					return true;
				}
				else if (NexusLoader.detailedDebug)
				{
					print("... registerPlayer() in AbstractMainEvent (canRegister()) for " + player.getPlayersName() + " player CANNOT join instancetype " + instance.getId());
				}
			}
		}
		player.sendMessage(LanguageEngine.getMsg("registering_noInstance"));
		return false;
	}
	
	protected boolean checkItems(PlayerEventInfo player)
	{
		return true;
	}
	
	public void playerUnregistered(PlayerEventInfo player)
	{
		for (Map.Entry<MainEventInstanceType, FastList<PlayerEventInfo>> e : _tempPlayers.entrySet())
		{
			synchronized (_tempPlayers)
			{
				for (PlayerEventInfo pi : e.getValue())
				{
					if (pi.getPlayersId() == player.getPlayersId())
					{
						(_tempPlayers.get(e.getKey())).remove(pi);
						if (NexusLoader.detailedDebug)
						{
							print("... playerUnregistered player " + player.getPlayersName() + " removed from _tempPlayers");
						}
						return;
					}
				}
				
			}
		}
		if ((_firstRegistered != null) && (_firstRegistered.contains(player)))
		{
			_firstRegistered.remove(player);
		}
		if (NexusLoader.detailedDebug)
		{
			print("... palyerUnregistered couldn't remove player " + player.getPlayersName() + " from _tempPlayers");
		}
	}
	
	public boolean canStart()
	{
		if (EventManager.getInstance().getMainEventManager().getPlayersCount() < getInt("minPlayers"))
		{
			return false;
		}
		return true;
	}
	
	protected void reorganizeInstances()
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: calling reorganizeInstance");
		}
		List<MainEventInstanceType> sameStrenghtInstances = new FastList<>();
		
		dumpTempPlayers();
		for (int currentStrenght = 1; currentStrenght <= 10; currentStrenght++)
		{
			for (Map.Entry<MainEventInstanceType, FastList<PlayerEventInfo>> e : _tempPlayers.entrySet())
			{
				if (!isFull(e.getKey()))
				{
					if (e.getKey().getStrenghtRate() == currentStrenght)
					{
						sameStrenghtInstances.add(e.getKey());
					}
				}
			}
			Collections.sort(sameStrenghtInstances, (i1, i2) ->
			{
				int neededPlayers1 = i1.getConfigInt("minPlayers") - (_tempPlayers.get(i1)).size();
				int neededPlayers2 = i2.getConfigInt("minPlayers") - (_tempPlayers.get(i2)).size();
				
				return neededPlayers1 < neededPlayers2 ? -1 : neededPlayers1 == neededPlayers2 ? 0 : 1;
			});
			reorganize(sameStrenghtInstances);
			sameStrenghtInstances.clear();
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: instances DONE reorganized!!");
		}
		dumpTempPlayers();
	}
	
	private void dumpTempPlayers()
	{
		if (NexusLoader.detailedDebug)
		{
			print("***** AbstractMainEvent: STARTING tempPlayers dump");
		}
		try
		{
			for (Map.Entry<MainEventInstanceType, FastList<PlayerEventInfo>> e : _tempPlayers.entrySet())
			{
				if (NexusLoader.detailedDebug)
				{
					print("... ***** AbstractMainEvent: instance " + e.getKey().getName() + " (" + e.getKey().getId() + ") has " + (e.getValue()).size() + " players");
				}
			}
		}
		catch (Exception e)
		{
			if (NexusLoader.detailedDebug)
			{
				print("error while dumping temp players - " + NexusLoader.getTraceString(e.getStackTrace()));
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("***** AbstractMainEvent: ENDED tempPlayers dump");
		}
	}
	
	protected void reorganize(List<MainEventInstanceType> instances)
	{
		for (MainEventInstanceType instance : instances)
		{
			if (hasEnoughtPlayers(instance))
			{
				instances.remove(instance);
			}
			else
			{
				int count = (_tempPlayers.get(instance)).size();
				int toMove = instance.getConfigInt("minPlayers") - count;
				for (MainEventInstanceType possibleInstance : instances)
				{
					if (possibleInstance != instance)
					{
						int moved = movePlayers(instance, possibleInstance, toMove);
						toMove -= moved;
						if (toMove == 0)
						{
							instances.remove(instance);
							break;
						}
						if (toMove > 0)
						{
						}
					}
				}
			}
		}
		if (!instances.isEmpty())
		{
			int minPlayers = Integer.MAX_VALUE;
			MainEventInstanceType inst = null;
			for (MainEventInstanceType instance : instances)
			{
				if (instance.getConfigInt("minPlayers") < minPlayers)
				{
					minPlayers = instance.getConfigInt("minPlayers");
					inst = instance;
				}
			}
			for (MainEventInstanceType instance : instances)
			{
				if (instance != inst)
				{
					movePlayers(inst, instance, -1);
				}
			}
			System.out.println("*** Done, instance " + inst.getName() + " has " + (_tempPlayers.get(inst)).size() + " players.");
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: reorganize() - instance " + inst.getName() + " has " + (_tempPlayers.get(inst)).size() + " players");
			}
		}
	}
	
	protected int movePlayers(MainEventInstanceType target, MainEventInstanceType source, int count)
	{
		if (count == 0)
		{
			return 0;
		}
		int moved = 0;
		for (PlayerEventInfo player : _tempPlayers.get(source))
		{
			(_tempPlayers.get(target)).add(player);
			(_tempPlayers.get(source)).remove(player);
			
			moved++;
			if ((count != -1) && (moved >= count))
			{
				break;
			}
		}
		return moved;
	}
	
	protected boolean isFull(MainEventInstanceType instance)
	{
		return (_tempPlayers.get(instance)).size() >= instance.getConfigInt("maxPlayers");
	}
	
	protected boolean hasEnoughtPlayers(MainEventInstanceType instance)
	{
		return (_tempPlayers.get(instance)).size() >= instance.getConfigInt("minPlayers");
	}
	
	protected boolean dividePlayers()
	{
		MainEventInstanceType currentInstance;
		int strenght;
		int playersCount;
		boolean joinStrongerInstIfNeeded;
		int maxDiff;
		
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: starting dividePlayers");
		}
		
		reorganizeInstances();
		
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: starting notEnoughtPlayersInstance operations");
		}
		List<MainEventInstanceType> notEnoughtPlayersInstances = new FastList<>();
		for (Map.Entry<MainEventInstanceType, FastList<PlayerEventInfo>> e : _tempPlayers.entrySet())
		{
			if ((e.getValue()).size() < e.getKey().getConfigInt("minPlayers"))
			{
				notEnoughtPlayersInstances.add(e.getKey());
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: notEnoughtPlayersInstances size = " + notEnoughtPlayersInstances.size());
		}
		List<MainEventInstanceType> fixed = new FastList<>();
		for (Object element : notEnoughtPlayersInstances)
		{
			currentInstance = (MainEventInstanceType) element;
			if ((currentInstance != null) && (!fixed.contains(currentInstance)))
			{
				strenght = currentInstance.getStrenghtRate();
				playersCount = (_tempPlayers.get(currentInstance)).size();
				
				joinStrongerInstIfNeeded = currentInstance.getConfigBoolean("joinStrongerInstIfNeeded");
				maxDiff = currentInstance.getConfigInt("joinStrongerInstMaxDiff");
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: iterating through notEnoughtInstances: " + currentInstance.getId() + " [" + currentInstance.getStrenghtRate() + "] - playersCount (" + playersCount + "), strenght (" + strenght + ")");
				}
				for (MainEventInstanceType possibleInstance : notEnoughtPlayersInstances)
				{
					if ((possibleInstance != null) && (!fixed.contains(possibleInstance)) && (possibleInstance != currentInstance))
					{
						playersCount = (_tempPlayers.get(currentInstance)).size();
						if (possibleInstance.getStrenghtRate() == strenght)
						{
							if ((((_tempPlayers.get(possibleInstance)).size() + playersCount) >= possibleInstance.getConfigInt("minPlayers")) && (NexusLoader.detailedDebug))
							{
								print("How could have this happened? (" + currentInstance.getName() + ", " + possibleInstance.getName() + ")");
							}
						}
						else if ((joinStrongerInstIfNeeded) && (possibleInstance.getStrenghtRate() > strenght))
						{
							if ((possibleInstance.getStrenghtRate() - strenght) <= maxDiff)
							{
								if (NexusLoader.detailedDebug)
								{
									print("AbstractMainEvent: /// possible instance " + possibleInstance.getName() + "[" + possibleInstance.getStrenghtRate() + "] - playersCount (" + (_tempPlayers.get(possibleInstance)).size() + "), strenght (" + possibleInstance.getStrenghtRate() + ")");
								}
								int sumPlayers = (_tempPlayers.get(possibleInstance)).size() + playersCount;
								if (sumPlayers >= possibleInstance.getConfigInt("minPlayers"))
								{
									int max = possibleInstance.getConfigInt("maxPlayers");
									int toMove;
									if (sumPlayers > max)
									{
										toMove = max - (_tempPlayers.get(possibleInstance)).size();
									}
									else
									{
										toMove = (_tempPlayers.get(currentInstance)).size();
									}
									if (NexusLoader.detailedDebug)
									{
										print("AbstractMainEvent: /*/*/ moving " + toMove + " players from " + currentInstance.getName() + " to " + possibleInstance.getName());
									}
									movePlayers(possibleInstance, currentInstance, toMove);
									if (NexusLoader.detailedDebug)
									{
										print("AbstractMainEvent: /*/*/ size of " + possibleInstance.getName() + " is now " + (_tempPlayers.get(possibleInstance)).size());
									}
									if ((_tempPlayers.get(possibleInstance)).size() >= possibleInstance.getConfigInt("minPlayers"))
									{
										if (NexusLoader.detailedDebug)
										{
											print("AbstractMainEvent: /*/*/ instance " + possibleInstance.getName() + " removed from notEnoughtPlayersInstances.");
										}
										fixed.add(possibleInstance);
									}
								}
							}
						}
					}
				}
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: reorganizing notEnoughtPlayers first part done");
		}
		dumpTempPlayers();
		for (Object element : notEnoughtPlayersInstances)
		{
			currentInstance = (MainEventInstanceType) element;
			
			playersCount = (_tempPlayers.get(currentInstance)).size();
			if (playersCount != 0)
			{
				strenght = currentInstance.getStrenghtRate();
				
				joinStrongerInstIfNeeded = currentInstance.getConfigBoolean("joinStrongerInstIfNeeded");
				maxDiff = currentInstance.getConfigInt("joinStrongerInstMaxDiff");
				for (MainEventInstanceType fixedInstance : fixed)
				{
					if ((joinStrongerInstIfNeeded) && (fixedInstance.getStrenghtRate() > strenght))
					{
						if ((fixedInstance.getStrenghtRate() - strenght) <= maxDiff)
						{
							int sumPlayers = (_tempPlayers.get(fixedInstance)).size();
							if (sumPlayers < fixedInstance.getConfigInt("maxPlayers"))
							{
								int toMove = fixedInstance.getConfigInt("maxPlayers") - (_tempPlayers.get(fixedInstance)).size();
								movePlayers(fixedInstance, currentInstance, toMove);
							}
						}
					}
				}
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: reorganizing notEnoughtPlayers second part done");
		}
		dumpTempPlayers();
		
		int c = 0;
		for (MainEventInstanceType toRemove : fixed)
		{
			notEnoughtPlayersInstances.remove(toRemove);
			c++;
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: fixed " + c + " notEnoughtPlayers instances");
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: starting tempPlayers reorganizations");
		}
		for (Map.Entry<MainEventInstanceType, FastList<PlayerEventInfo>> e : _tempPlayers.entrySet())
		{
			playersCount = e.getValue().size();
			if (playersCount != 0)
			{
				strenght = e.getKey().getStrenghtRate();
				
				joinStrongerInstIfNeeded = e.getKey().getConfigBoolean("joinStrongerInstIfNeeded");
				maxDiff = e.getKey().getConfigInt("joinStrongerInstMaxDiff");
				if (!hasEnoughtPlayers(e.getKey()))
				{
					while (playersCount > 0)
					{
						int temp = playersCount;
						for (Map.Entry<MainEventInstanceType, FastList<PlayerEventInfo>> i : _tempPlayers.entrySet())
						{
							if (playersCount <= 0)
							{
								break;
							}
							if (hasEnoughtPlayers(i.getKey()))
							{
								if (i.getKey().getStrenghtRate() == strenght)
								{
									int canMove = i.getKey().getConfigInt("maxPlayers") - (i.getValue()).size();
									if (canMove > 0)
									{
										if (movePlayers(i.getKey(), e.getKey(), 1) == 1)
										{
											playersCount--;
										}
									}
								}
							}
						}
						if (playersCount == temp)
						{
							break;
						}
					}
					if ((playersCount != 0) && (joinStrongerInstIfNeeded))
					{
						if (playersCount > 0)
						{
							int temp = playersCount;
							for (Map.Entry<MainEventInstanceType, FastList<PlayerEventInfo>> i : _tempPlayers.entrySet())
							{
								if (playersCount <= 0)
								{
									break;
								}
								if (hasEnoughtPlayers(i.getKey()))
								{
									if (i.getKey().getStrenghtRate() > strenght)
									{
										if ((i.getKey().getStrenghtRate() - strenght) <= maxDiff)
										{
											int canMove = i.getKey().getConfigInt("maxPlayers") - (i.getValue()).size();
											if (canMove > 0)
											{
												if (movePlayers(i.getKey(), e.getKey(), 1) == 1)
												{
													playersCount--;
												}
											}
										}
									}
								}
							}
							if (playersCount != temp)
							{
								break;
							}
						}
					}
				}
			}
			
		}
		if (NexusLoader.detailedDebug)
		{
			print("* AbstractMainEvent: instances organizing FINISHED:");
		}
		dumpTempPlayers();
		for (MainEventInstanceType inst : notEnoughtPlayersInstances)
		{
			int i = 0;
			for (PlayerEventInfo player : _tempPlayers.get(inst))
			{
				player.screenMessage(LanguageEngine.getMsg("registering_notEnoughtPlayers"), getEventName(), true);
				_manager.unregisterPlayer(player, true);
				i++;
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: ... Not enought players for instance " + inst.getName() + " (" + (_tempPlayers.get(inst)).size() + "), instance removed; " + i + " players unregistered.");
			}
			_tempPlayers.remove(inst);
		}
		int aviableInstances = 0;
		
		_instances = new InstanceData[_tempPlayers.size()];
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: dividing players into teams - instances count = " + _tempPlayers.size());
		}
		for (Map.Entry<MainEventInstanceType, FastList<PlayerEventInfo>> e : _tempPlayers.entrySet())
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: STARTING event for instance: " + e.getKey().getName());
			}
			InstanceData instance = CallBack.getInstance().getOut().createInstance(e.getKey().getName(), (_manager.getRunTime() * 1000) + 60000, 0, true);
			_instances[aviableInstances] = instance;
			e.getKey().setInstance(instance);
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: ... created InstanceData, duration: " + ((_manager.getRunTime() * 1000) + 60000));
			}
			aviableInstances++;
			
			_teams.put(Integer.valueOf(instance.getId()), new FastMap<>());
			int teamsCount = initInstanceTeams(e.getKey(), instance.getId());
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: ... teamscount = " + teamsCount + "; DIVIND to teams:");
			}
			dividePlayersToTeams(instance.getId(), e.getValue(), teamsCount);
		}
		_tempPlayers.clear();
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: aviable instances = " + aviableInstances);
		}
		if (aviableInstances == 0)
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: evnet COULD NOT START due to lack of players in instances");
			}
			announce(LanguageEngine.getMsg("announce_noInstance"));
			clearEvent();
			return false;
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: ... dividePlayers allowed event to start!");
		}
		for (Map.Entry<Integer, FastMap<Integer, EventTeam>> i : _teams.entrySet())
		{
			CallbackManager.getInstance().eventStarts(i.getKey().intValue(), getEventType(), (i.getValue()).values());
			for (EventTeam team : (i.getValue()).values())
			{
				team.calcAverageLevel();
			}
		}
		return true;
	}
	
	protected void dividePlayersToTeams(int instanceId, FastList<PlayerEventInfo> players, int teamsCount)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: /// dividingplayers to teams for INSTANCE " + instanceId);
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: /// players count = " + players.size());
		}
		if ((!getEventType().isFFAEvent()) && (teamsCount > 1))
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: /// team based event");
			}
			String type = getString("divideToTeamsMethod");
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: /// using method: " + type);
			}
			Collections.sort(players, EventManager.getInstance().compareByLevels);
			if (type.startsWith("PvPs"))
			{
				Collections.sort(players, EventManager.getInstance().compareByPvps);
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: /// players sorted:");
			}
			FastMap<ClassType, FastList<PlayerEventInfo>> sortedPlayers = new FastMap<>();
			for (ClassType classType : ClassType.values())
			{
				sortedPlayers.put(classType, new FastList<>());
			}
			for (PlayerEventInfo pi : players)
			{
				(sortedPlayers.get(pi.getClassType())).add(pi);
			}
			for (Map.Entry<ClassType, FastList<PlayerEventInfo>> te : sortedPlayers.entrySet())
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: /// ... " + te.getKey().toString() + " - " + (te.getValue()).size() + " players");
				}
			}
			if (getBoolean("balanceHealersInTeams"))
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: /// balancing healers in teams");
				}
				int healersCount = (sortedPlayers.get(ClassType.Priest)).size();
				
				int teamId = 0;
				while (healersCount > 0)
				{
					teamId++;
					
					PlayerEventInfo player = sortedPlayers.get(ClassType.Priest).head().getNext().getValue();
					sortedPlayers.get(ClassType.Priest).remove(player);
					healersCount--;
					
					player.onEventStart(this);
					_teams.get(instanceId).get(teamId).addPlayer(player, true);
					if (teamId >= teamsCount)
					{
						teamId = 0;
					}
				}
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: /// healers balanced into teams:");
			}
			for (EventTeam team : _teams.get(instanceId).values())
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: /// team " + team.getTeamName() + " has " + team.getPlayers().size() + " healers");
				}
			}
			int teamId = 0;
			for (Map.Entry<ClassType, FastList<PlayerEventInfo>> e : sortedPlayers.entrySet())
			{
				for (PlayerEventInfo pi : e.getValue())
				{
					int leastPlayers = Integer.MAX_VALUE;
					for (EventTeam team : (_teams.get(Integer.valueOf(instanceId))).values())
					{
						if (team.getPlayers().size() < leastPlayers)
						{
							leastPlayers = team.getPlayers().size();
							teamId = team.getTeamId();
						}
					}
					pi.onEventStart(this);
					_teams.get(instanceId).get(teamId).addPlayer(pi, true);
				}
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: /// players divided:");
			}
			for (EventTeam team : _teams.get(instanceId).values())
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: /// team " + team.getTeamName() + " has " + team.getPlayers().size() + " PLAYERS");
				}
			}
		}
		else
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: /// FFA event");
			}
			for (PlayerEventInfo pi : players)
			{
				pi.onEventStart(this);
				_teams.get(instanceId).get(1).addPlayer(pi, true);
			}
		}
	}
	
	protected void createParties(int partySize)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: CREATING PARTIES... ");
		}
		for (Map.Entry<Integer, FastMap<Integer, EventTeam>> teams : _teams.entrySet())
		{
			if (NexusLoader.detailedDebug)
			{
				print("* AbstractMainEvent: PROCESSING INSTANCE " + teams.getKey() + " (creating parties)");
			}
			for (EventTeam team : (teams.getValue()).values())
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: / parties: processing team " + team.getTeamName());
				}
				int totalCount = 0;
				FastMap<ClassType, FastList<PlayerEventInfo>> players = new FastMap<>();
				for (ClassType classType : ClassType.values())
				{
					players.put(classType, new FastList<>());
				}
				for (PlayerEventInfo player : team.getPlayers())
				{
					if (player.isOnline())
					{
						players.get(player.getClassType()).add(player);
						totalCount++;
					}
				}
				for (List<PlayerEventInfo> pls : players.values())
				{
					Collections.sort(pls, EventManager.getInstance().compareByLevels);
				}
				int healersCount = players.get(ClassType.Priest).size();
				
				int partiesCount = (int) Math.ceil(totalCount / partySize);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: ////// total count of players in the team " + totalCount + "; PARTIES COUNT " + partiesCount + "; healers count " + healersCount);
				}
				FastList<PlayerEventInfo> toParty = new FastList<>();
				
				int healersToGive = (int) Math.ceil(healersCount / partiesCount);
				if (healersToGive == 0)
				{
					healersToGive = 1;
				}
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: ////// healersToGive to each party: " + healersToGive);
				}
				for (int i = 0; i < partiesCount; i++)
				{
					if (healersCount > 0)
					{
						for (int h = 0; (h < healersToGive) && (healersCount >= healersToGive); h++)
						{
							PlayerEventInfo pi = players.get(ClassType.Priest).head().getNext().getValue();
							if (pi == null)
							{
								pi = players.get(ClassType.Priest).head().getNext().getValue();
							}
							toParty.add(pi);
							players.get(ClassType.Priest).remove(pi);
							
							healersCount--;
						}
					}
					boolean b = false;
					while (toParty.size() < partySize)
					{
						boolean added = false;
						for (PlayerEventInfo fighter : players.get(b ? ClassType.Mystic : ClassType.Fighter))
						{
							toParty.add(fighter);
							players.get(b ? ClassType.Mystic : ClassType.Fighter).remove(fighter);
							added = true;
						}
						b = !b;
						if (!added)
						{
							for (PlayerEventInfo mystic : players.get(b ? ClassType.Mystic : ClassType.Fighter))
							{
								toParty.add(mystic);
								players.get(b ? ClassType.Mystic : ClassType.Fighter).remove(mystic);
								added = true;
							}
						}
						if (!added)
						{
							if (healersCount <= 0)
							{
								break;
							}
							for (PlayerEventInfo healer : players.get(ClassType.Priest))
							{
								toParty.add(healer);
								players.get(ClassType.Priest).remove(healer);
								added = true;
								healersCount--;
							}
						}
					}
					dumpParty(team, toParty);
					
					partyPlayers(toParty);
					toParty.clear();
				}
			}
		}
	}
	
	private void dumpParty(EventTeam team, FastList<PlayerEventInfo> players)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: ////// START of dump of party for team " + team.getTeamName());
		}
		for (PlayerEventInfo pl : players)
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: /*/*/*/*/*/*/ player " + pl.getPlayersName() + " is of class id " + pl.getClassType().toString());
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: ////// END of dump of party for team " + team.getTeamName());
		}
	}
	
	protected void partyPlayers(FastList<PlayerEventInfo> players)
	{
		try
		{
			if (players.size() > 1)
			{
				_partiesCount += 1;
				
				PartyData party = null;
				
				int count = 0;
				int nextDelay = 800;
				for (PlayerEventInfo player : players)
				{
					if (player.getParty() != null)
					{
						player.getParty().removePartyMember(player);
					}
					player.setCanInviteToParty(false);
				}
				PlayerEventInfo leader = null;
				for (PlayerEventInfo player : players)
				{
					if (count == 0)
					{
						leader = player;
						party = new PartyData(player);
					}
					else
					{
						CallBack.getInstance().getOut().scheduleGeneral(new AddToParty(party, player), nextDelay * count);
					}
					count++;
					if (count >= 9)
					{
						break;
					}
				}
				if (leader != null)
				{
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: reallowing inviting to the party back to the leader (" + leader.getPlayersName() + ").");
					}
					else if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: NOT reallowing inviting to the party back to the leader because he is null!");
					}
				}
				final PlayerEventInfo fLeader = leader;
				CallBack.getInstance().getOut().scheduleGeneral(() ->
				{
					if (fLeader != null)
					{
						fLeader.setCanInviteToParty(true);
					}
				}, 800 * (count + 1));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: createParties error (and parties will be deleted): " + NexusLoader.getTraceString(e.getStackTrace()));
			}
			debug("Error while partying players: " + e.toString() + ". Deleting parties...");
			try
			{
				for (PlayerEventInfo player : players)
				{
					if (player.getParty() != null)
					{
						player.getParty().removePartyMember(player);
					}
				}
			}
			catch (Exception e2)
			{
				e2.printStackTrace();
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: error while removing parties (cause of another error): " + NexusLoader.getTraceString(e2.getStackTrace()));
				}
			}
			debug("Parties deleted.");
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: parties deleted.");
			}
		}
	}
	
	private class AddToParty implements Runnable
	{
		private final PartyData party;
		private final PlayerEventInfo player;
		
		public AddToParty(PartyData party, PlayerEventInfo player)
		{
			this.party = party;
			this.player = player;
		}
		
		@SuppressWarnings("synthetic-access")
		@Override
		public void run()
		{
			try
			{
				if (party.exists())
				{
					if (player.getParty() != null)
					{
						player.getParty().removePartyMember(player);
					}
					if (party.getMemberCount() >= getInt("maxPartySize"))
					{
						return;
					}
					party.addPartyMember(player);
				}
				player.setCanInviteToParty(true);
			}
			catch (NullPointerException e)
			{
				e.printStackTrace();
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: error while adding players to the party: " + NexusLoader.getTraceString(e.getStackTrace()));
				}
				debug("error while adding players to the party: " + NexusLoader.getTraceString(e.getStackTrace()));
			}
		}
	}
	
	protected void teleportPlayers(int instanceId, SpawnType type, boolean ffa)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: ========================================");
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: STARTING TO TELEPORT PLAYERS (ffa = " + ffa + ")");
		}
		boolean removeBuffs = getBoolean("removeBuffsOnStart");
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: removeBuffs = " + removeBuffs);
		}
		int i = 0;
		for (PlayerEventInfo player : getPlayers(instanceId))
		{
			EventSpawn spawn = getSpawn(type, ffa ? -1 : player.getTeamId());
			if (spawn == null)
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: ! Missing spawn for team " + ((_teams.get(Integer.valueOf(instanceId))).size() == 1 ? -1 : player.getTeamId()) + ", map " + _manager.getMap().getMapName() + ", event " + getEventType().getAltTitle() + " !!");
				}
				NexusLoader.debug("Missing spawn for team " + ((_teams.get(Integer.valueOf(instanceId))).size() == 1 ? -1 : player.getTeamId()) + ", map " + _manager.getMap().getMapName() + ", event " + getEventType().getAltTitle() + " !!", Level.SEVERE);
			}
			int radius = spawn.getRadius();
			if (radius == -1)
			{
				radius = 50;
			}
			Loc loc = new Loc(spawn.getLoc().getX(), spawn.getLoc().getY(), spawn.getLoc().getZ());
			loc.addRadius(radius);
			
			player.teleport(loc, 0, false, instanceId);
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: /// player " + player.getPlayersName() + " teleported to " + loc.getX() + ", " + loc.getY() + ", " + loc.getZ() + " (radius = " + radius + "), SPAWN ID " + spawn.getSpawnId() + ", SPAWN TEAM " + spawn.getSpawnTeam());
			}
			if (removeBuffs)
			{
				player.removeBuffs();
			}
			i++;
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: " + i + " PLAYERS TELEPORTED");
		}
		clearMapHistory(-1, type);
	}
	
	protected boolean checkPlayers(int instanceId)
	{
		if (!checkIfEventCanContinue(instanceId, null))
		{
			announce(instanceId, LanguageEngine.getMsg("announce_alldisconnected"));
			endInstance(instanceId, true, false, true);
			
			debug(getEventName() + ": no players left in the teams after teleporting to the event, the fight can't continue. The event has been aborted!");
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: check players: FALSE (NOT ENOUGHT players to start the event)");
			}
			return false;
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: check players: OK (enought players to start the event)");
		}
		return true;
	}
	
	protected void enableMarkers(int instanceId, boolean useEventSpawnMarkers)
	{
		if (!_enableRadar)
		{
			return;
		}
		for (EventTeam team : (_teams.get(Integer.valueOf(instanceId))).values())
		{
			for (PlayerEventInfo pi : team.getPlayers())
			{
				pi.createRadar();
			}
		}
		List<EventSpawn> markers;
		EventSpawn marker = null;
		if (useEventSpawnMarkers)
		{
			markers = null;
			for (EventTeam team : (_teams.get(Integer.valueOf(instanceId))).values())
			{
				markers = _manager.getMap().getMarkers(team.getTeamId());
				if ((markers != null) && (!markers.isEmpty()))
				{
					for (EventSpawn pMarkers : markers)
					{
						marker = pMarkers;
						for (PlayerEventInfo pi : team.getPlayers())
						{
							pi.getRadar().setLoc(marker.getLoc().getX(), marker.getLoc().getY(), marker.getLoc().getZ());
							pi.getRadar().setRepeat(true);
							pi.getRadar().enable();
						}
					}
				}
			}
		}
		
	}
	
	protected void removeStaticDoors(int instanceId)
	{
		try
		{
			CallBack.getInstance().getOut().addDoorToInstance(instanceId, 17190001, true);
			CallBack.getInstance().getOut().getInstanceDoors(instanceId)[0].openMe();
		}
		catch (Exception e)
		{
			NexusLoader.debug("tried to removeStaticDoors, but an error occured - " + e.toString());
		}
	}
	
	protected void disableMarkers(int instanceId)
	{
		if (!_enableRadar)
		{
			return;
		}
		for (EventTeam team : (_teams.get(Integer.valueOf(instanceId))).values())
		{
			for (PlayerEventInfo pi : team.getPlayers())
			{
				pi.getRadar().disable();
			}
		}
	}
	
	protected void addMarker(PlayerEventInfo pi, EventSpawn marker, boolean repeat)
	{
		if (!_enableRadar)
		{
			return;
		}
		pi.getRadar().setLoc(marker.getLoc().getX(), marker.getLoc().getY(), marker.getLoc().getZ());
		
		pi.getRadar().setRepeat(repeat);
		if (!pi.getRadar().isEnabled())
		{
			pi.getRadar().enable();
		}
	}
	
	protected void removeMarker(PlayerEventInfo pi, EventSpawn marker)
	{
		pi.removeRadarMarker(marker.getLoc().getX(), marker.getLoc().getY(), marker.getLoc().getZ());
	}
	
	protected void setupTitles(int instanceId)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: SETUPING TITLES");
		}
		for (PlayerEventInfo pi : getPlayers(instanceId))
		{
			if (_allowSchemeBuffer)
			{
				EventBuffer.getInstance().buffPlayer(pi, true);
			}
			if (_removePartiesOnStart)
			{
				PartyData pt = pi.getParty();
				if (pt != null)
				{
					pi.getParty().removePartyMember(pi);
				}
			}
			if (pi.isTitleUpdated())
			{
				pi.setTitle(getTitle(pi), true);
			}
		}
	}
	
	protected EventSpawn getSpawn(SpawnType type, int teamId)
	{
		EventMap map = _manager.getMap();
		if (map == null)
		{
			return null;
		}
		return map.getNextSpawn(teamId, type);
	}
	
	protected void clearMapHistory(int teamId, SpawnType type)
	{
		EventMap map = _manager.getMap();
		if (map != null)
		{
			map.clearHistory(teamId, type);
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: map history clean done");
			}
		}
		else if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: couldn't clean map, map is NULL!");
		}
	}
	
	protected void forceSitAll(int instanceId)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: FORCE SIT ALL");
		}
		for (PlayerEventInfo player : getPlayers(instanceId))
		{
			player.abortCasting();
			
			player.disableAfkCheck(true);
			player.sitDown();
		}
	}
	
	protected void forceStandAll(int instanceId)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: FORCE STAND UP");
		}
		for (PlayerEventInfo player : getPlayers(instanceId))
		{
			player.disableAfkCheck(false);
			player.standUp();
		}
	}
	
	protected void sysMsgToAll(String text)
	{
		if (NexusLoader.detailedDebug)
		{
			print("? AbstractMainEvent: sysMsgToAll - " + text);
		}
		for (PlayerEventInfo pi : getPlayers(0))
		{
			pi.sendMessage(text);
		}
	}
	
	protected void sysMsgToAll(int instance, String text)
	{
		if (NexusLoader.detailedDebug)
		{
			print("? AbstractMainEvent: sysMsgToAll to instance " + instance + "; text= " + text);
		}
		for (PlayerEventInfo pi : getPlayers(instance))
		{
			pi.sendMessage(text);
		}
	}
	
	public Set<PlayerEventInfo> getPlayers(int instanceId)
	{
		Set<PlayerEventInfo> players = new FastSet<>();
		if (_teams.isEmpty())
		{
			return players;
		}
		if ((instanceId == 0) || (instanceId == 1) || (instanceId == -1))
		{
			for (FastMap<Integer, EventTeam> fm : _teams.values())
			{
				for (EventTeam team : fm.values())
				{
					for (PlayerEventInfo player : team.getPlayers())
					{
						players.add(player);
					}
				}
			}
		}
		else
		{
			for (EventTeam team : (_teams.get(Integer.valueOf(instanceId))).values())
			{
				for (PlayerEventInfo player : team.getPlayers())
				{
					players.add(player);
				}
			}
		}
		return players;
	}
	
	protected void initWaweRespawns(int delay)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: STARTING WAWE SPAWN SYSTEM");
		}
		_waweScheduler = new WaweRespawnScheduler(delay * 1000);
	}
	
	public void addSpectator(PlayerEventInfo gm, int instanceId)
	{
		if ((gm.isInEvent()) || (gm.isRegistered()))
		{
			gm.sendMessage(LanguageEngine.getMsg("observing_alreadyRegistered"));
			return;
		}
		if (_spectators != null)
		{
			EventSpawn selected = null;
			for (EventSpawn s : EventManager.getInstance().getMainEventManager().getMap().getSpawns())
			{
				if ((s.getSpawnType() == SpawnType.Regular) || (s.getSpawnType() == SpawnType.Safe))
				{
					selected = s;
				}
			}
			if (selected == null)
			{
				gm.sendMessage(LanguageEngine.getMsg("observing_noSpawn"));
				return;
			}
			gm.initOrigInfo();
			
			gm.setInstanceId(instanceId);
			gm.teleToLocation(selected.getLoc().getX(), selected.getLoc().getY(), selected.getLoc().getZ(), false);
			synchronized (_spectators)
			{
				_spectators.add(gm);
			}
		}
	}
	
	public void removeSpectator(PlayerEventInfo gm)
	{
		if (_spectators != null)
		{
			gm.setInstanceId(0);
			gm.teleToLocation(gm.getOrigLoc().getX(), gm.getOrigLoc().getY(), gm.getOrigLoc().getZ(), false);
			synchronized (_spectators)
			{
				_spectators.remove(gm);
			}
		}
	}
	
	public boolean isWatching(PlayerEventInfo gm)
	{
		return (_spectators != null) && (_spectators.contains(gm));
	}
	
	protected void clearPlayers(boolean unregister, int instanceId)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent:  =====================");
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: CALLED CLEAR PLAYERS for instanceId " + instanceId + ", unregister = " + unregister);
		}
		if (instanceId == 0)
		{
			if (_waweScheduler != null)
			{
				_waweScheduler.stop();
			}
			EventManager.getInstance().clearDisconnectedPlayers();
		}
		if (_spectators != null)
		{
			for (PlayerEventInfo spectator : _spectators)
			{
				if ((instanceId == 0) || (spectator.getInstanceId() == instanceId))
				{
					spectator.setInstanceId(0);
					spectator.teleToLocation(spectator.getOrigLoc().getX(), spectator.getOrigLoc().getY(), spectator.getOrigLoc().getZ(), false);
					
					_spectators.remove(spectator);
				}
			}
			if (instanceId == 0)
			{
				_spectators.clear();
				_spectators = null;
			}
		}
		cleanMap(instanceId);
		
		int unregistered = 0;
		if (unregister)
		{
			switch (_manager.getState())
			{
				case REGISTERING:
					for (PlayerEventInfo player : _manager.getPlayers())
					{
						player.setIsRegisteredToMainEvent(false, null);
						CallBack.getInstance().getPlayerBase().eventEnd(player);
						
						unregistered++;
					}
					break;
				case END:
				case RUNNING:
				case TELE_BACK:
					_manager.paralizeAll(false);
					for (PlayerEventInfo player : getPlayers(instanceId))
					{
						_manager.getPlayers().remove(player);
						if (player.getEventTeam() != null)
						{
							player.getEventTeam().removePlayer(player);
						}
						player.setIsRegisteredToMainEvent(false, null);
						CallBack.getInstance().getPlayerBase().eventEnd(player);
						
						unregistered++;
					}
					for (PlayerEventInfo player : _manager.getPlayers())
					{
						if ((instanceId == 0) || (player.getInstanceId() == instanceId))
						{
							player.setIsRegisteredToMainEvent(false, null);
							CallBack.getInstance().getPlayerBase().eventEnd(player);
							
							unregistered++;
						}
					}
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: unregistered " + unregistered + " players");
		}
		if (_instances != null)
		{
			for (InstanceData instance : _instances)
			{
				if ((instanceId == 0) || (instanceId == instance.getId()))
				{
					CallbackManager.getInstance().eventEnded(instanceId, getEventType(), (_teams.get(Integer.valueOf(instance.getId()))).values());
					for (EventTeam team : _teams.get(instance.getId()).values())
					{
						for (PlayerEventInfo pi : team.getPlayers())
						{
							team.removePlayer(pi);
						}
					}
				}
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: Event " + getEventName() + " finished clearPlayers() for instance ID " + instanceId);
		}
		NexusLoader.debug("Event " + getEventName() + " finished clearPlayers() for instance ID " + instanceId);
		if (instanceId == 0)
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: checking if all unregistered...");
			}
			Collection<PlayerEventInfo> playersLeft = getPlayers(0);
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: playersLeft size = " + playersLeft.size());
			}
			if ((playersLeft.size() > 0) || (_manager.getPlayers().size() > 0))
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: the event hasn't cleaned itself properly, perfoming additional cleanings...");
				}
				NexusLoader.debug("The event hasn't cleant itself properly. There was an error while running the event, propably. Cleaning it other way.", Level.WARNING);
				for (PlayerEventInfo player : playersLeft)
				{
					if (player.getEventTeam() != null)
					{
						player.getEventTeam().removePlayer(player);
					}
					player.setIsRegisteredToMainEvent(false, null);
					CallBack.getInstance().getPlayerBase().eventEnd(player);
				}
				playersLeft = _manager.getPlayers();
				for (PlayerEventInfo player : playersLeft)
				{
					if (player.getEventTeam() != null)
					{
						player.getEventTeam().removePlayer(player);
					}
					player.setIsRegisteredToMainEvent(false, null);
					CallBack.getInstance().getPlayerBase().eventEnd(player);
				}
				playersLeft = getPlayers(0);
				if (playersLeft.size() == 0)
				{
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: additional cleanings finished");
					}
					NexusLoader.debug("Additional cleaning finished. All players unregistered successfully now.");
				}
			}
		}
		if (instanceId == 0)
		{
			_tempPlayers.clear();
			_rewardedInstances.clear();
			_instances = null;
			
			_manager.clean(null);
		}
	}
	
	public void setKillsStats(PlayerEventInfo player, int ammount)
	{
	}
	
	public void setDeathsStats(PlayerEventInfo player, int ammount)
	{
	}
	
	public void setScoreStats(PlayerEventInfo player, int ammount)
	{
	}
	
	@Override
	public RewardPosition[] getRewardTypes()
	{
		return _rewardTypes;
	}
	
	public void setRewardTypes(RewardPosition[] types)
	{
		_rewardTypes = types;
	}
	
	public String getString(String propName)
	{
		if (_configs.containsKey(propName))
		{
			String value = _configs.get(propName).getValue();
			return value;
		}
		return "";
	}
	
	public int getInt(String propName)
	{
		if (_configs.containsKey(propName))
		{
			int value = _configs.get(propName).getValueInt();
			return value;
		}
		return 0;
	}
	
	public boolean getBoolean(String propName)
	{
		if (_configs.containsKey(propName))
		{
			return _configs.get(propName).getValueBoolean();
		}
		return false;
	}
	
	protected void addConfig(ConfigModel model)
	{
		_configs.put(model.getKey(), model);
	}
	
	protected void removeConfig(String key)
	{
		_configs.remove(key);
	}
	
	protected void addConfig(String category, ConfigModel model)
	{
		if (!_configCategories.contains(category))
		{
			_configCategories.add(category);
		}
		_configs.put(model.getKey(), model.setCategory(category));
	}
	
	protected void addMapConfig(ConfigModel model)
	{
		_mapConfigs.put(model.getKey(), model);
	}
	
	protected void addInstanceTypeConfig(ConfigModel model)
	{
		_instanceTypeConfigs.put(model.getKey(), model);
	}
	
	protected void removeConfigs()
	{
		_configCategories.clear();
		_configs.clear();
	}
	
	protected void removeMapConfigs()
	{
		_mapConfigs.clear();
	}
	
	protected void removeInstanceTypeConfigs()
	{
		_instanceTypeConfigs.clear();
	}
	
	@Override
	public final Map<String, ConfigModel> getConfigs()
	{
		return _configs;
	}
	
	@Override
	public void clearConfigs()
	{
		removeConfigs();
		removeMapConfigs();
		removeInstanceTypeConfigs();
	}
	
	@Override
	public FastList<String> getCategories()
	{
		return _configCategories;
	}
	
	@Override
	public void setConfig(String key, String value, boolean addToValue)
	{
		if (!_configs.containsKey(key))
		{
			return;
		}
		if (!addToValue)
		{
			_configs.get(key).setValue(value);
		}
		else
		{
			_configs.get(key).addToValue(value);
		}
	}
	
	@Override
	public Map<String, ConfigModel> getMapConfigs()
	{
		return _mapConfigs;
	}
	
	@Override
	public Map<SpawnType, String> getAviableSpawnTypes()
	{
		return _spawnTypes;
	}
	
	public int getMaxPlayers()
	{
		return getInt("maxPlayers");
	}
	
	public String getEstimatedTimeLeft()
	{
		return "N/A";
	}
	
	@Override
	public boolean canRun(EventMap map)
	{
		return getMissingSpawns(map).length() == 0;
	}
	
	@Override
	public abstract String getMissingSpawns(EventMap paramEventMap);
	
	protected String addMissingSpawn(SpawnType type, int team, int count)
	{
		return "<font color=B46F6B>" + getEventType().getAltTitle() + "</font> -> <font color=9f9f9f>No</font> <font color=B46F6B>" + type.toString().toUpperCase() + "</font> <font color=9f9f9f>spawn for team " + team + " " + (team == 0 ? "(team doesn't matter)" : "") + " count " + count + " (or more)</font><br1>";
	}
	
	public void announce(int instance, String msg)
	{
		for (PlayerEventInfo pi : getPlayers(instance))
		{
			pi.creatureSay(getEventName() + ": " + msg, getEventName(), 18);
		}
		if (_spectators != null)
		{
			for (PlayerEventInfo spectator : _spectators)
			{
				if ((spectator.isOnline()) && (spectator.getInstanceId() == instance))
				{
					spectator.creatureSay(getEventName() + ": " + msg, getEventName(), 18);
				}
			}
		}
	}
	
	public void announce(int instance, String msg, int team)
	{
		if (NexusLoader.detailedDebug)
		{
			print("? AbstractMainEvent: announcing to instance " + instance + " team " + team + " msg: " + msg);
		}
		for (PlayerEventInfo pi : getPlayers(instance))
		{
			if (pi.getTeamId() == team)
			{
				pi.creatureSay(getEventName() + ": " + msg, getEventName(), 18);
			}
		}
		if (_spectators != null)
		{
			for (PlayerEventInfo spectator : _spectators)
			{
				if ((spectator.isOnline()) && (spectator.getInstanceId() == instance))
				{
					spectator.creatureSay(getEventName() + ": " + msg, getEventName() + " [T" + team + " msg]", 18);
				}
			}
		}
	}
	
	public void announceToAllTeamsBut(int instance, String msg, int excludedTeam)
	{
		if (NexusLoader.detailedDebug)
		{
			print("? AbstractMainEvent: announcing to all teams but " + excludedTeam + ", instance " + instance + " msg " + msg);
		}
		for (PlayerEventInfo pi : getPlayers(instance))
		{
			if (pi.getTeamId() != excludedTeam)
			{
				pi.creatureSay(getEventName() + ": " + msg, getEventName(), 18);
			}
		}
		if (_spectators != null)
		{
			for (PlayerEventInfo spectator : _spectators)
			{
				if ((spectator.isOnline()) && (spectator.getInstanceId() == instance))
				{
					spectator.creatureSay(getEventName() + ": " + msg, getEventName() + " [all except T" + excludedTeam + " msg]", 18);
				}
			}
		}
	}
	
	public void screenAnnounce(int instance, String msg)
	{
		if (NexusLoader.detailedDebug)
		{
			print("? AbstractMainEvent: screenannounce to instance " + instance + " msg: " + msg);
		}
		for (PlayerEventInfo pi : getPlayers(instance))
		{
			pi.creatureSay(msg, getEventName(), 15);
		}
		if (_spectators != null)
		{
			for (PlayerEventInfo spectator : _spectators)
			{
				if ((spectator.isOnline()) && (spectator.getInstanceId() == instance))
				{
					spectator.creatureSay(msg, getEventName(), 15);
				}
			}
		}
	}
	
	protected void scheduleRevive(PlayerEventInfo pi, int time)
	{
		new ReviveTask(pi, time);
	}
	
	protected class WaweRespawnScheduler implements Runnable
	{
		private ScheduledFuture<?> _future;
		private final int _delay;
		private final FastList<PlayerEventInfo> _players;
		
		public WaweRespawnScheduler(int delay)
		{
			_delay = delay;
			_future = CallBack.getInstance().getOut().scheduleGeneral(this, delay);
			_players = new FastList<>();
		}
		
		public void addPlayer(PlayerEventInfo player)
		{
			synchronized (_players)
			{
				_players.add(player);
			}
			player.screenMessage(LanguageEngine.getMsg("event_revive", new Object[]
			{
				Long.valueOf(Math.max(1L, _future.getDelay(TimeUnit.SECONDS)))
			}), getEventType().getAltTitle(), true);
			player.sendMessage(LanguageEngine.getMsg("event_revive", new Object[]
			{
				Long.valueOf(Math.max(1L, _future.getDelay(TimeUnit.SECONDS)))
			}));
		}
		
		public void stop()
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: stopping wawe spawn scheduler");
			}
			_players.clear();
			if (_future != null)
			{
				_future.cancel(false);
			}
			_future = null;
		}
		
		@Override
		public void run()
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: running wawe spawn scheduler...");
			}
			int count = 0;
			synchronized (_players)
			{
				for (PlayerEventInfo pi : _players)
				{
					if ((pi != null) && (pi.isDead()))
					{
						count++;
						new ReviveTask(pi);
					}
				}
				_players.clear();
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: ...wawe scheduler respawned " + count + " players");
			}
			_future = CallBack.getInstance().getOut().scheduleGeneral(this, _delay);
		}
	}
	
	private class ReviveTask implements Runnable
	{
		private final PlayerEventInfo player;
		private final int instance;
		
		protected ReviveTask(PlayerEventInfo p, int time)
		{
			player = p;
			instance = player.getInstanceId();
			CallBack.getInstance().getOut().scheduleGeneral(this, time);
			
			player.sendMessage(LanguageEngine.getMsg("event_revive", new Object[]
			{
				Integer.valueOf(time / 1000)
			}));
		}
		
		protected ReviveTask(PlayerEventInfo p)
		{
			player = p;
			instance = player.getInstanceId();
			
			CallBack.getInstance().getOut().executeTask(this);
		}
		
		/*
		 * private ReviveTask(PlayerEventInfo p, int time, int instance) { player = p; instance = player.getInstanceId(); CallBack.getInstance().getOut().scheduleGeneral(this, time); player.sendMessage(LanguageEngine.getMsg("event_revive", new Object[] { Integer.valueOf(time / 1000) })); }
		 */
		@Override
		public void run()
		{
			if ((player.getActiveEvent() != null) && (player.isDead()))
			{
				player.doRevive();
				if (_allowSchemeBuffer)
				{
					EventBuffer.getInstance().buffPlayer(player);
					EventBuffer.getInstance().buffPet(player);
				}
				player.setCurrentCp(player.getMaxCp());
				player.setCurrentHp(player.getMaxHp());
				player.setCurrentMp(player.getMaxMp());
				
				player.setTitle(getTitle(player), true);
				
				respawnPlayer(player, instance);
				if (getBoolean("removeBuffsOnRespawn"))
				{
					player.removeBuffs();
				}
			}
		}
	}
	
	public class Clock implements Runnable
	{
		private final AbstractMainEvent.AbstractEventInstance _event;
		private int time;
		private boolean _announcesCountdown = true;
		protected ScheduledFuture<?> _task = null;
		
		public Clock(AbstractMainEvent.AbstractEventInstance instance)
		{
			_event = instance;
		}
		
		public String getTime()
		{
			String mins = "" + (time / 60);
			String secs = "" + (time % 60);
			return "" + mins + ":" + secs + "";
		}
		
		public void disableAnnouncingCountdown()
		{
			_announcesCountdown = false;
		}
		
		@Override
		public void run()
		{
			try
			{
				clockTick();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			if (_allowScoreBar)
			{
				if (_instances != null)
				{
					for (InstanceData instance : _instances)
					{
						try
						{
							scorebarText = getScorebar(instance.getId());
						}
						catch (Exception e)
						{
							e.printStackTrace();
							if (NexusLoader.detailedDebug)
							{
								print("ERROR on CLOCK.getScorebar: " + NexusLoader.getTraceString(e.getStackTrace()));
							}
							if (NexusLoader.detailedDebug)
							{
								print("Event aborted");
							}
							for (InstanceData ins : _instances)
							{
								announce(ins.getId(), LanguageEngine.getMsg("event_mysteriousError"));
							}
							clearEvent();
							return;
						}
						if (scorebarText != null)
						{
							for (PlayerEventInfo player : getPlayers(instance.getId()))
							{
								player.sendEventScoreBar(scorebarText);
							}
							if (_spectators != null)
							{
								for (PlayerEventInfo spec : _spectators)
								{
									if (spec.getInstanceId() == instance.getId())
									{
										spec.sendEventScoreBar(scorebarText);
									}
								}
							}
						}
					}
				}
			}
			if (_announcesCountdown)
			{
				switch (time)
				{
					case 60:
					case 300:
					case 600:
					case 1200:
					case 1800:
						announce(_event.getInstance().getId(), LanguageEngine.getMsg("event_countdown_min", new Object[]
						{
							Integer.valueOf(time / 60)
						}));
						break;
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 10:
					case 30:
						announce(_event.getInstance().getId(), LanguageEngine.getMsg("event_countdown_sec", new Object[]
						{
							Integer.valueOf(time)
						}));
				}
			}
			if (time <= 0)
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: Clock.time is " + time + ", scheduling next event task");
				}
				_task = _event.scheduleNextTask(0);
			}
			else
			{
				setTime(time - 1, false);
				_task = CallBack.getInstance().getOut().scheduleGeneral(this, 1000L);
			}
		}
		
		public void abort()
		{
			if (_task != null)
			{
				_task.cancel(false);
			}
		}
		
		public synchronized void setTime(int t, boolean debug)
		{
			if ((debug) && (NexusLoader.detailedDebug))
			{
				print("AbstractMainEvent: setting value of Clock.time to " + t);
			}
			time = t;
		}
		
		public void startClock(int mt)
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: starting Clock and setting Clock.time to " + mt);
			}
			time = mt;
			CallBack.getInstance().getOut().scheduleGeneral(this, 1L);
		}
	}
	
	protected void setInstanceNotReceiveRewards(int instanceId)
	{
		synchronized (_rewardedInstances)
		{
			_rewardedInstances.add(Integer.valueOf(instanceId));
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: instance of ID " + instanceId + " has been marked as NOTREWARDED");
		}
	}
	
	protected void rewardFirstRegisteredFFA(List<PlayerEventInfo> list)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: rewarding first registered (ffa)");
		}
		int count = 0;
		if (list != null)
		{
			for (PlayerEventInfo player : list)
			{
				if ((_firstRegistered.contains(player)) && (player.isOnline()))
				{
					player.sendMessage(LanguageEngine.getMsg("event_extraReward", new Object[]
					{
						Integer.valueOf(firstRegisteredRewardCount)
					}));
					EventRewardSystem.getInstance().rewardPlayer(getEventType(), 1, player, RewardPosition.FirstRegistered, null, player.getTotalTimeAfk(), 0, 0);
					
					count++;
				}
			}
		}
		else
		{
			for (PlayerEventInfo player : _firstRegistered)
			{
				player.sendMessage(LanguageEngine.getMsg("event_extraReward", new Object[]
				{
					Integer.valueOf(firstRegisteredRewardCount)
				}));
				EventRewardSystem.getInstance().rewardPlayer(getEventType(), 1, player, RewardPosition.FirstRegistered, null, player.getTotalTimeAfk(), 0, 0);
				
				count++;
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: " + count + " players were given FirstRegistered reward");
		}
	}
	
	protected void rewardFirstRegistered(List<EventTeam> list)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: rewarding first registered (teams)");
		}
		int count = 0;
		if (list != null)
		{
			for (EventTeam t : list)
			{
				for (PlayerEventInfo player : t.getPlayers())
				{
					if ((_firstRegistered.contains(player)) && (player.isOnline()))
					{
						player.sendMessage(LanguageEngine.getMsg("event_extraReward", new Object[]
						{
							Integer.valueOf(firstRegisteredRewardCount)
						}));
						EventRewardSystem.getInstance().rewardPlayer(getEventType(), 1, player, RewardPosition.FirstRegistered, null, player.getTotalTimeAfk(), 0, 0);
						
						count++;
					}
				}
			}
		}
		else
		{
			for (PlayerEventInfo player : _firstRegistered)
			{
				player.sendMessage(LanguageEngine.getMsg("event_extraReward", new Object[]
				{
					Integer.valueOf(firstRegisteredRewardCount)
				}));
				EventRewardSystem.getInstance().rewardPlayer(getEventType(), 1, player, RewardPosition.FirstRegistered, null, player.getTotalTimeAfk(), 0, 0);
				
				count++;
			}
		}
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: " + count + " players were given FirstRegistered reward");
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void rewardAllPlayersFromTeam(int instanceId, int minScore, int minKills, int teamId)
	{
		try
		{
			if (getEventType().isFFAEvent())
			{
				NexusLoader.debug(getEventName() + " cannot use rewardAllPlayers since this is a FFA event.", Level.SEVERE);
				return;
			}
			if (_instances == null)
			{
				NexusLoader.debug(getEventName() + " _instances were null when the event tried to reward!", Level.SEVERE);
				return;
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: CALLED REWARD ALL PLAYERS  for instance " + instanceId + ", min score " + minScore + ", min kills " + minKills);
			}
			boolean firstXRewardWinners = "WinnersOnly".equals(firstRegisteredRewardType);
			int playersCount;
			int place;
			for (InstanceData instance : _instances)
			{
				if ((instance.getId() == instanceId) || (instanceId == -1))
				{
					synchronized (_rewardedInstances)
					{
						if (!_rewardedInstances.contains(Integer.valueOf(instance.getId())))
						{
							_rewardedInstances.add(Integer.valueOf(instance.getId()));
						}
						else
						{
							continue;
						}
					}
					EventTeam team = (_teams.get(Integer.valueOf(instance.getId()))).get(Integer.valueOf(teamId));
					if (team == null)
					{
						NexusLoader.debug(getEventName() + " no team of ID " + teamId + " to be rewarded!", Level.SEVERE);
						return;
					}
					playersCount = team.getPlayers().size();
					
					FastList<PlayerEventInfo> sorted = new FastList<>();
					Map<PlayerEventInfo, Integer> map = new FastMap<>();
					for (PlayerEventInfo player : team.getPlayers())
					{
						sorted.add(player);
					}
					Collections.sort(sorted, EventManager.getInstance().comparePlayersScore);
					for (PlayerEventInfo player : sorted)
					{
						map.put(player, Integer.valueOf(getPlayerData(player).getScore()));
					}
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: before giving reward");
					}
					Object scores = EventRewardSystem.getInstance().rewardPlayers(map, getEventType(), 1, minScore, _afkHalfReward, _afkNoReward);
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: rewards given");
					}
					place = 1;
					int limitToAnnounce = getInt("announcedTopPlayersCount");
					int totalLimit = Math.min(limitToAnnounce * 2, 15);
					int counter = 1;
					for (Map.Entry<Integer, List<PlayerEventInfo>> e : ((Map<Integer, List<PlayerEventInfo>>) scores).entrySet())
					{
						if (counter > totalLimit)
						{
							break;
						}
						if (place <= limitToAnnounce)
						{
							for (PlayerEventInfo player : e.getValue())
							{
								if (counter > totalLimit)
								{
									break;
								}
								announce(instance.getId(), LanguageEngine.getMsg("event_announceScore", new Object[]
								{
									Integer.valueOf(place),
									player.getPlayersName(),
									Integer.valueOf(getPlayerData(player).getScore())
								}));
								counter++;
							}
							place++;
						}
					}
					place = 1;
					for (Map.Entry<Integer, List<PlayerEventInfo>> i : ((Map<Integer, List<PlayerEventInfo>>) scores).entrySet())
					{
						if (place == 1)
						{
							if (firstXRewardWinners)
							{
								rewardFirstRegisteredFFA(i.getValue());
							}
							if ((i.getValue()).size() > 1)
							{
								if (playersCount > (i.getValue()).size())
								{
									TextBuilder tb = new TextBuilder("*** ");
									for (PlayerEventInfo player : i.getValue())
									{
										tb.append(LanguageEngine.getMsg("event_ffa_announceWinner2_part1", new Object[]
										{
											player.getPlayersName()
										}) + " ");
									}
									String s = tb.toString();
									tb = new TextBuilder(s.substring(0, s.length() - 4));
									tb.append(LanguageEngine.getMsg("event_ffa_announceWinner2_part2"));
									
									announce(instance.getId(), tb.toString());
								}
								else
								{
									announce(instance.getId(), "*** " + LanguageEngine.getMsg("event_ffa_announceWinner3"));
								}
							}
							else
							{
								announce(instance.getId(), "*** " + LanguageEngine.getMsg("event_ffa_announceWinner1", new Object[]
								{
									(i.getValue()).get(0).getPlayersName()
								}));
							}
							for (PlayerEventInfo player : i.getValue())
							{
								getPlayerData(player).getGlobalStats().raise(GlobalStats.GlobalStatType.WINS, 1);
							}
						}
						else
						{
							for (PlayerEventInfo player : i.getValue())
							{
								getPlayerData(player).getGlobalStats().raise(GlobalStats.GlobalStatType.LOSES, 1);
							}
						}
						place++;
					}
				}
			}
			if (!firstXRewardWinners)
			{
				rewardFirstRegisteredFFA(null);
			}
			saveGlobalStats(instanceId);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	protected void rewardAllPlayers(int instanceId, int minScore, int minKills)
	{
		try
		{
			if (!getEventType().isFFAEvent())
			{
				NexusLoader.debug(getEventName() + " cannot use rewardAllPlayers since it is an non-FFA event.", Level.SEVERE);
				return;
			}
			if (_instances == null)
			{
				NexusLoader.debug(getEventName() + " _instances were null when the event tried to reward!", Level.SEVERE);
				return;
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: CALLED REWARD ALL PLAYERS  for instance " + instanceId + ", min score " + minScore + ", min kills " + minKills);
			}
			boolean firstXRewardWinners = "WinnersOnly".equals(firstRegisteredRewardType);
			int playersCount;
			int place;
			for (InstanceData instance : _instances)
			{
				if ((instance.getId() == instanceId) || (instanceId == -1))
				{
					synchronized (_rewardedInstances)
					{
						if (!_rewardedInstances.contains(Integer.valueOf(instance.getId())))
						{
							_rewardedInstances.add(Integer.valueOf(instance.getId()));
						}
						else
						{
							continue;
						}
					}
					playersCount = getPlayers(instance.getId()).size();
					
					FastList<PlayerEventInfo> sorted = new FastList<>();
					Map<PlayerEventInfo, Integer> map = new FastMap<>();
					for (PlayerEventInfo player : getPlayers(instance.getId()))
					{
						sorted.add(player);
					}
					Collections.sort(sorted, EventManager.getInstance().comparePlayersScore);
					for (PlayerEventInfo player : sorted)
					{
						map.put(player, Integer.valueOf(getPlayerData(player).getScore()));
					}
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: before giving reward");
					}
					Map<Integer, List<PlayerEventInfo>> scores = EventRewardSystem.getInstance().rewardPlayers(map, getEventType(), 1, minScore, _afkHalfReward, _afkNoReward);
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: rewards given");
					}
					place = 1;
					int limitToAnnounce = getInt("announcedTopPlayersCount");
					int totalLimit = Math.min(limitToAnnounce * 2, 15);
					int counter = 1;
					for (Map.Entry<Integer, List<PlayerEventInfo>> e : scores.entrySet())
					{
						if (counter > totalLimit)
						{
							break;
						}
						if (place <= limitToAnnounce)
						{
							for (PlayerEventInfo player : e.getValue())
							{
								if (counter > totalLimit)
								{
									break;
								}
								announce(instance.getId(), LanguageEngine.getMsg("event_announceScore", new Object[]
								{
									Integer.valueOf(place),
									player.getPlayersName(),
									Integer.valueOf(getPlayerData(player).getScore())
								}));
								counter++;
							}
							place++;
						}
					}
					place = 1;
					for (Map.Entry<Integer, List<PlayerEventInfo>> i : scores.entrySet())
					{
						if (place == 1)
						{
							if (firstXRewardWinners)
							{
								rewardFirstRegisteredFFA(i.getValue());
							}
							if ((i.getValue()).size() > 1)
							{
								if (playersCount > (i.getValue()).size())
								{
									TextBuilder tb = new TextBuilder("*** ");
									for (PlayerEventInfo player : i.getValue())
									{
										tb.append(LanguageEngine.getMsg("event_ffa_announceWinner2_part1", new Object[]
										{
											player.getPlayersName()
										}) + " ");
									}
									String s = tb.toString();
									tb = new TextBuilder(s.substring(0, s.length() - 4));
									tb.append(LanguageEngine.getMsg("event_ffa_announceWinner2_part2"));
									
									announce(instance.getId(), tb.toString());
								}
								else
								{
									announce(instance.getId(), "*** " + LanguageEngine.getMsg("event_ffa_announceWinner3"));
								}
							}
							else
							{
								announce(instance.getId(), "*** " + LanguageEngine.getMsg("event_ffa_announceWinner1", new Object[]
								{
									(i.getValue()).get(0).getPlayersName()
								}));
							}
							for (PlayerEventInfo player : i.getValue())
							{
								getPlayerData(player).getGlobalStats().raise(GlobalStats.GlobalStatType.WINS, 1);
							}
						}
						else
						{
							for (PlayerEventInfo player : i.getValue())
							{
								getPlayerData(player).getGlobalStats().raise(GlobalStats.GlobalStatType.LOSES, 1);
							}
						}
						place++;
					}
				}
			}
			if (!firstXRewardWinners)
			{
				rewardFirstRegisteredFFA(null);
			}
			saveGlobalStats(instanceId);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	protected void rewardAllTeams(int instanceId, int minScore, int minKills)
	{
		try
		{
			if (getEventType().isFFAEvent())
			{
				NexusLoader.debug(getEventName() + " cannot use rewardAllTeams since it is an FFA event.");
				return;
			}
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: CALLED REWARD ALL TEAMS  for instance " + instanceId + ", min score " + minScore + ", min kills " + minKills);
			}
			boolean firstXRewardWinners = "WinnersOnly".equals(firstRegisteredRewardType);
			for (InstanceData instance : _instances)
			{
				if ((instance.getId() == instanceId) || (instanceId == -1))
				{
					synchronized (_rewardedInstances)
					{
						if (!_rewardedInstances.contains(Integer.valueOf(instance.getId())))
						{
							_rewardedInstances.add(Integer.valueOf(instance.getId()));
						}
						else
						{
							continue;
						}
					}
					int teamsCount = (_teams.get(Integer.valueOf(instance.getId()))).size();
					
					FastList<EventTeam> sorted = new FastList<>();
					Map<EventTeam, Integer> map = new FastMap<>();
					for (EventTeam team : (_teams.get(Integer.valueOf(instance.getId()))).values())
					{
						sorted.add(team);
					}
					Collections.sort(sorted, EventManager.getInstance().compareTeamScore);
					for (EventTeam team : sorted)
					{
						map.put(team, Integer.valueOf(team.getScore()));
					}
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: before giving reward");
					}
					Map<Integer, List<EventTeam>> scores = EventRewardSystem.getInstance().rewardTeams(map, getEventType(), 1, minScore, _afkHalfReward, _afkNoReward);
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: rewards given");
					}
					int place = 1;
					for (EventTeam team : sorted)
					{
						announce(instance.getId(), LanguageEngine.getMsg("event_announceScore", new Object[]
						{
							Integer.valueOf(place),
							team.getFullName(),
							Integer.valueOf(team.getScore())
						}));
						team.setFinalPosition(place);
						place++;
					}
					place = 1;
					for (Map.Entry<Integer, List<EventTeam>> i : scores.entrySet())
					{
						if (place == 1)
						{
							if (firstXRewardWinners)
							{
								rewardFirstRegistered(i.getValue());
							}
							if ((i.getValue()).size() > 1)
							{
								if (teamsCount > (i.getValue()).size())
								{
									TextBuilder tb = new TextBuilder("*** ");
									for (EventTeam team : i.getValue())
									{
										tb.append(LanguageEngine.getMsg("event_team_announceWinner2_part1", new Object[]
										{
											team.getFullName()
										}) + " ");
									}
									String s = tb.toString();
									tb = new TextBuilder(s.substring(0, s.length() - 4));
									tb.append(LanguageEngine.getMsg("event_team_announceWinner2_part2"));
									
									announce(instance.getId(), tb.toString());
								}
								else
								{
									announce(instance.getId(), "*** " + LanguageEngine.getMsg("event_team_announceWinner3"));
								}
							}
							else
							{
								announce(instance.getId(), "*** " + LanguageEngine.getMsg("event_team_announceWinner1", new Object[]
								{
									(i.getValue()).get(0).getFullName()
								}));
							}
							for (EventTeam team : i.getValue())
							{
								for (PlayerEventInfo player : team.getPlayers())
								{
									getPlayerData(player).getGlobalStats().raise(GlobalStats.GlobalStatType.WINS, 1);
								}
							}
						}
						else
						{
							for (EventTeam team : i.getValue())
							{
								for (PlayerEventInfo player : team.getPlayers())
								{
									getPlayerData(player).getGlobalStats().raise(GlobalStats.GlobalStatType.LOSES, 1);
								}
							}
						}
						place++;
					}
					saveGlobalStats(instance.getId());
				}
			}
			if (!firstXRewardWinners)
			{
				rewardFirstRegistered(null);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	protected void saveGlobalStats(int instance)
	{
		Map<PlayerEventInfo, GlobalStatsModel> stats = new FastMap<>();
		for (PlayerEventInfo player : getPlayers(instance))
		{
			getPlayerData(player).getGlobalStats().raise(GlobalStats.GlobalStatType.COUNT_PLAYED, 1);
			stats.put(player, getPlayerData(player).getGlobalStats());
		}
		EventStatsManager.getInstance().getGlobalStats().updateGlobalStats(stats);
	}
	
	protected NpcData spawnNPC(int x, int y, int z, int npcId, int instanceId, String name, String title)
	{
		if (NexusLoader.detailedDebug)
		{
			print("AbstractMainEvent: spawning npc " + x + ", " + y + ", " + z + ", npc id " + npcId + ", instance " + instanceId + ", name " + name + ", title " + title);
		}
		NpcTemplateData template = new NpcTemplateData(npcId);
		if (!template.exists())
		{
			return null;
		}
		template.setSpawnName(name);
		template.setSpawnTitle(title);
		try
		{
			NpcData npc = template.doSpawn(x, y, z, 1, instanceId);
			if (npc != null)
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: npc spawned succesfully.");
				}
				else if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: npc null after spawning (template exists = " + template.exists() + ").");
				}
			}
			return npc;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: error while spawning npc - " + NexusLoader.getTraceString(e.getStackTrace()));
			}
		}
		return null;
	}
	
	public void insertConfigs(MainEventInstanceType type)
	{
		for (Map.Entry<String, ConfigModel> e : _instanceTypeConfigs.entrySet())
		{
			type.addDefaultConfig(e.getKey(), e.getValue().getValue(), e.getValue().getDesc(), e.getValue().getDefaultVal(), e.getValue().getInput(), e.getValue().getInputParams());
		}
	}
	
	public void addInstanceType(MainEventInstanceType type)
	{
		_types.put(Integer.valueOf(type.getId()), type);
	}
	
	public void removeInstanceType(MainEventInstanceType type)
	{
		_types.remove(Integer.valueOf(type.getId()));
	}
	
	public MainEventInstanceType getInstanceType(int id)
	{
		return _types.get(Integer.valueOf(id));
	}
	
	public FastMap<Integer, MainEventInstanceType> getInstanceTypes()
	{
		return _types;
	}
	
	public InstanceData[] getInstances()
	{
		return _instances;
	}
	
	public int getTeamsCountInInstance(int instance)
	{
		return (_teams.get(Integer.valueOf(instance))).size();
	}
	
	private final Object firstBloodLock = new Object();
	
	protected void tryFirstBlood(PlayerEventInfo killer)
	{
		synchronized (firstBloodLock)
		{
			if (!_firstBlood)
			{
				for (RewardPosition pos : getRewardTypes())
				{
					if (pos == RewardPosition.FirstBlood)
					{
						_firstBloodPlayer = killer;
						if (getBoolean("firstBloodMessage"))
						{
							screenAnnounce(killer.getInstanceId(), LanguageEngine.getMsg("event_firstBlood", new Object[]
							{
								killer.getPlayersName()
							}));
						}
						EventRewardSystem.getInstance().rewardPlayer(getEventType(), 1, killer, RewardPosition.FirstBlood, null, 0, 0, 0);
						if (!NexusLoader.detailedDebug)
						{
							break;
						}
						print("AbstractMainEvent: FIRST BLOOD reward given to " + killer.getPlayersName());
						break;
					}
				}
				_firstBlood = true;
			}
		}
	}
	
	protected void giveOnKillReward(PlayerEventInfo killer)
	{
		for (RewardPosition pos : getRewardTypes())
		{
			if (pos == RewardPosition.OnKill)
			{
				EventRewardSystem.getInstance().rewardPlayer(getEventType(), 1, killer, RewardPosition.OnKill, null, 0, 0, 0);
				break;
			}
		}
	}
	
	protected void giveKillingSpreeReward(EventPlayerData killerData)
	{
		if ((killerData instanceof PvPEventPlayerData))
		{
			int spree = ((PvPEventPlayerData) killerData).getSpree();
			if (EventRewardSystem.getInstance().rewardPlayer(getEventType(), 1, killerData.getOwner(), RewardPosition.KillingSpree, String.valueOf(spree), 0, 0, 0))
			{
				killerData.getOwner().sendMessage("You have been awarded for your " + spree + " kills in row!");
			}
		}
	}
	
	public String getScorebarCb(int instance)
	{
		int teamsCount = getTeamsCountInInstance(instance);
		TextBuilder tb = new TextBuilder();
		if (teamsCount > 1)
		{
			tb.append("<table width=510 bgcolor=3E3E3E><tr><td width=510 align=center><font color=ac9887>Score:</font> ");
			
			int i = 0;
			for (EventTeam team : (_teams.get(instance)).values())
			{
				i++;
				if (teamsCount > 3)
				{
					if (i != teamsCount)
					{
						tb.append("<font color=" + EventManager.getInstance().getTeamColorForHtml(team.getTeamId()) + ">" + team.getTeamName() + "</font><font color=9f9f9f> - " + team.getScore() + "  |  </font>");
					}
					else
					{
						tb.append("<font color=" + EventManager.getInstance().getTeamColorForHtml(team.getTeamId()) + ">" + team.getTeamName() + "</font><font color=9f9f9f> - " + team.getScore() + "</font>");
					}
				}
				else if (i != teamsCount)
				{
					tb.append("<font color=" + EventManager.getInstance().getTeamColorForHtml(team.getTeamId()) + ">" + team.getFullName() + "</font><font color=9f9f9f> - " + team.getScore() + "  |  </font>");
				}
				else
				{
					tb.append("<font color=" + EventManager.getInstance().getTeamColorForHtml(team.getTeamId()) + ">" + team.getFullName() + "</font><font color=9f9f9f> - " + team.getScore() + "</font>");
				}
			}
			tb.append("</td></tr></table>");
		}
		return tb.toString();
	}
	
	public String getEventInfoCb(int instance, Object param)
	{
		TextBuilder tb = new TextBuilder();
		try
		{
			int teamsCount = getTeamsCountInInstance(instance);
			
			List<EventTeam> teams = new FastList<>();
			teams.addAll((_teams.get(Integer.valueOf(instance))).values());
			
			Collections.sort(teams, EventManager.getInstance().compareTeamScore);
			if (teamsCount == 2)
			{
				tb.append(addExtraEventInfoCb(instance));
				
				tb.append("<br><img src=\"L2UI.SquareBlank\" width=510 height=3>");
				tb.append("<img src=\"L2UI.SquareGray\" width=512 height=2>");
				tb.append("<img src=\"L2UI.SquareBlank\" width=510 height=3>");
				
				tb.append("<table width=510 bgcolor=2E2E2E>");
				
				boolean firstTeam = true;
				for (EventTeam team : teams)
				{
					if (firstTeam)
					{
						tb.append("<tr><td width=250 align=center><font color=" + EventManager.getInstance().getTeamColorForHtml(team.getTeamId()) + ">1. " + team.getFullName() + "</font> <font color=6f6f6f>(" + team.getPlayers().size() + " players; " + team.getAverageLevel() + " avg lvl)</font></td>");
					}
					else
					{
						tb.append("<td width=10></td><td width=250 align=center><font color=" + EventManager.getInstance().getTeamColorForHtml(team.getTeamId()) + ">2. " + team.getFullName() + "</font> <font color=6f6f6f>(" + team.getPlayers().size() + " players; " + team.getAverageLevel() + " avg lvl)</font></td></tr>");
					}
					firstTeam = false;
				}
				tb.append("<tr></tr>");
				
				int countTopScorers = _countOfShownTopPlayers;
				Map<Integer, List<PlayerEventInfo>> topPlayers = new FastMap<>();
				FastList<PlayerEventInfo> temp = new FastList<>();
				
				int counter = 0;
				for (EventTeam team : teams)
				{
					topPlayers.put(Integer.valueOf(team.getTeamId()), new FastList<>());
					temp.addAll(team.getPlayers());
					
					Collections.sort(temp, EventManager.getInstance().comparePlayersScore);
					if (temp.size() < countTopScorers)
					{
						countTopScorers = temp.size();
					}
					for (PlayerEventInfo player : temp)
					{
						(topPlayers.get(Integer.valueOf(team.getTeamId()))).add(player);
						
						counter++;
						if (counter >= countTopScorers)
						{
							break;
						}
					}
					temp.clear();
					counter = 0;
				}
				firstTeam = true;
				for (int i = 0; i < countTopScorers;)
				{
					if (firstTeam)
					{
						PlayerEventInfo tempPlayer = (topPlayers.get(Integer.valueOf(1))).get(i);
						tb.append("<tr><td width=250 align=center><font color=9f9f9f>" + (i + 1) + ". " + tempPlayer.getPlayersName() + "</font><font color=" + EventManager.getInstance().getDarkColorForHtml(1) + "> - " + tempPlayer.getScore() + " score</font></td>");
					}
					else
					{
						PlayerEventInfo tempPlayer = (topPlayers.get(Integer.valueOf(2))).get(i);
						tb.append("<td width=10></td><td width=250 align=center><font color=9f9f9f>" + (i + 1) + ". " + tempPlayer.getPlayersName() + "</font><font color=" + EventManager.getInstance().getDarkColorForHtml(2) + "> - " + tempPlayer.getScore() + " score</font></td></tr>");
					}
					firstTeam = !firstTeam;
					if (firstTeam)
					{
						i++;
					}
				}
				tb.append("</table>");
				tb.append("<img src=\"L2UI.SquareBlank\" width=510 height=3>");
				tb.append("<img src=\"L2UI.SquareGray\" width=512 height=2>");
			}
			else if (teamsCount == 1)
			{
				tb.append(addExtraEventInfoCb(instance));
				
				tb.append("<br><img src=\"L2UI.SquareBlank\" width=510 height=3>");
				tb.append("<img src=\"L2UI.SquareGray\" width=512 height=2>");
				tb.append("<img src=\"L2UI.SquareBlank\" width=510 height=3>");
				
				tb.append("<table width=510 bgcolor=2E2E2E>");
				
				List<PlayerEventInfo> tempPlayers = new FastList<>();
				tempPlayers.addAll(getPlayers(instance));
				Collections.sort(tempPlayers, EventManager.getInstance().comparePlayersScore);
				
				int countTopPlayers = _countOfShownTopPlayers;
				int i = 0;
				for (PlayerEventInfo player : tempPlayers)
				{
					String kd = String.valueOf(player.getDeaths() == 0 ? player.getKills() : player.getKills() / player.getDeaths());
					kd = kd.substring(0, Math.min(3, kd.length()));
					
					tb.append("<tr><td width=510 align=center><font color=9f9f9f>" + (i + 1) + ".</font> <font color=ac9887>" + player.getPlayersName() + "</font><font color=7f7f7f> - " + player.getScore() + " points</font>  <font color=5f5f5f>(K:D ratio: " + kd + ")</font></td>");
					tb.append("</tr>");
					
					i++;
					if (i >= countTopPlayers)
					{
						break;
					}
				}
				tb.append("</table>");
				
				tb.append("<img src=\"L2UI.SquareBlank\" width=510 height=3>");
				tb.append("<img src=\"L2UI.SquareGray\" width=512 height=2>");
			}
			else if (teamsCount > 2)
			{
				int page = (param != null) && ((param instanceof Integer)) ? ((Integer) param).intValue() : 1;
				int maxPages = (int) Math.ceil(teamsCount - 1);
				
				int countTopScorers = _countOfShownTopPlayers;
				
				int shownTeam1Id = 1;
				int shownTeam2Id = 2;
				if (page > 1)
				{
					shownTeam1Id += page - 1;
					shownTeam2Id += page - 1;
				}
				tb.append(addExtraEventInfoCb(instance));
				
				tb.append("<br><img src=\"L2UI.SquareBlank\" width=510 height=3>");
				tb.append("<img src=\"L2UI.SquareGray\" width=512 height=2>");
				tb.append("<img src=\"L2UI.SquareBlank\" width=510 height=3>");
				
				tb.append("<table width=510 bgcolor=2E2E2E>");
				
				boolean firstTeam = true;
				for (EventTeam team : teams)
				{
					if ((team.getTeamId() == shownTeam1Id) || (team.getTeamId() == shownTeam2Id))
					{
						if (firstTeam)
						{
							tb.append("<tr><td width=250 align=center><font color=" + EventManager.getInstance().getTeamColorForHtml(team.getTeamId()) + ">" + shownTeam1Id + ". " + team.getFullName() + "</font> <font color=6f6f6f>(" + team.getPlayers().size() + " players; " + team.getAverageLevel() + " avg lvl)</font></td>");
						}
						else
						{
							tb.append("<td width=10></td><td width=250 align=center><font color=" + EventManager.getInstance().getTeamColorForHtml(team.getTeamId()) + ">" + shownTeam2Id + ". " + team.getFullName() + "</font> <font color=6f6f6f>(" + team.getPlayers().size() + " players; " + team.getAverageLevel() + " avg lvl)</font></td></tr>");
						}
						firstTeam = false;
					}
				}
				tb.append("<tr></tr>");
				
				Map<Integer, List<PlayerEventInfo>> topPlayers = new FastMap<>();
				FastList<PlayerEventInfo> temp = new FastList<>();
				
				int counter = 0;
				for (EventTeam team : teams)
				{
					if ((team.getTeamId() == shownTeam1Id) || (team.getTeamId() == shownTeam2Id))
					{
						topPlayers.put(Integer.valueOf(team.getTeamId()), new FastList<>());
						temp.addAll(team.getPlayers());
						
						Collections.sort(temp, EventManager.getInstance().comparePlayersScore);
						if (temp.size() < countTopScorers)
						{
							countTopScorers = temp.size();
						}
						for (PlayerEventInfo player : temp)
						{
							(topPlayers.get(Integer.valueOf(team.getTeamId()))).add(player);
							
							counter++;
							if (counter >= countTopScorers)
							{
								break;
							}
						}
						temp.clear();
						counter = 0;
					}
				}
				firstTeam = true;
				for (int i = 0; i < countTopScorers;)
				{
					if (firstTeam)
					{
						PlayerEventInfo tempPlayer = (topPlayers.get(Integer.valueOf(shownTeam1Id))).get(i);
						tb.append("<tr><td width=250 align=center><font color=9f9f9f>" + (i + 1) + ". " + tempPlayer.getPlayersName() + "</font><font color=" + EventManager.getInstance().getDarkColorForHtml(shownTeam1Id) + "> - " + tempPlayer.getScore() + " score</font></td>");
					}
					else
					{
						PlayerEventInfo tempPlayer = (topPlayers.get(Integer.valueOf(shownTeam2Id))).get(i);
						tb.append("<td width=10></td><td width=250 align=center><font color=9f9f9f>" + (i + 1) + ". " + tempPlayer.getPlayersName() + "</font><font color=" + EventManager.getInstance().getDarkColorForHtml(shownTeam2Id) + "> - " + tempPlayer.getScore() + " score</font></td></tr>");
					}
					firstTeam = !firstTeam;
					if (firstTeam)
					{
						i++;
					}
				}
				tb.append("</table>");
				tb.append("<img src=\"L2UI.SquareBlank\" width=510 height=3>");
				tb.append("<img src=\"L2UI.SquareGray\" width=512 height=2>");
				tb.append("<img src=\"L2UI.SquareBlank\" width=510 height=3>");
				
				boolean previousButton = false;
				boolean nextButton = false;
				if (page > 1)
				{
					previousButton = true;
				}
				if (page < maxPages)
				{
					nextButton = true;
				}
				if ((nextButton) && (previousButton))
				{
					tb.append("<table width=510 bgcolor=2E2E2E><tr><td width=200 align=left><button value=\"Prev page\" action=\"bypass -h " + EventHtmlManager.BBS_COMMAND + " nextpageteam " + (page - 1) + " " + instance + "\" width=85 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>" + "<td width=200 align=right><button value=\"Next page\" action=\"bypass -h " + EventHtmlManager.BBS_COMMAND + " nextpageteam " + (page + 1) + " " + instance + "\" width=85 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
				}
				else if (nextButton)
				{
					tb.append("<table width=510 bgcolor=2E2E2E><tr><td width=510 align=right><button value=\"Next page\" action=\"bypass -h " + EventHtmlManager.BBS_COMMAND + " nextpageteam " + (page + 1) + " " + instance + "\" width=85 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
				}
				else if (previousButton)
				{
					tb.append("<table width=510 bgcolor=2E2E2E><tr><td width=510 align=left><button value=\"Prev page\" action=\"bypass -h " + EventHtmlManager.BBS_COMMAND + " nextpageteam " + (page - 1) + " " + instance + "\" width=85 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
				}
				tb.append("</table>");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return tb.toString();
	}
	
	protected String addExtraEventInfoCb(int instance)
	{
		boolean firstBloodEnabled = false;
		for (RewardPosition pos : getRewardTypes())
		{
			if (pos == RewardPosition.FirstBlood)
			{
				firstBloodEnabled = true;
				break;
			}
		}
		if (firstBloodEnabled)
		{
			return "<table width=510 bgcolor=3E3E3E><tr><td width=510 align=center><font color=CE7171>First blood:</font><font color=7f7f7f> " + (_firstBloodPlayer != null ? _firstBloodPlayer.getPlayersName() : "None yet") + "</font></td></tr></table>";
		}
		return "";
	}
	
	@Override
	public String getDescriptionForReward(RewardPosition reward)
	{
		if (reward == RewardPosition.FirstRegistered)
		{
			String type = getString("firstRegisteredRewardType");
			if (type.equals("All"))
			{
				return "The reward for the " + getInt("firstRegisteredRewardCount") + " first registered players, given in the end of the event. <br1>Check out event configs for more customization.";
			}
			if (type.equals("WinnersOnly"))
			{
				return "The reward for the " + getInt("firstRegisteredRewardCount") + " first registered players, given in the end of the event only if the players won the event. <br1>Check out event configs for more customization.";
			}
		}
		return null;
	}
	
	protected boolean canJoinInstance(PlayerEventInfo player, MainEventInstanceType instance)
	{
		int minLvl = instance.getConfigInt("minLvl");
		int maxLvl = instance.getConfigInt("maxLvl");
		if (((maxLvl != -1) && (player.getLevel() > maxLvl)) || (player.getLevel() < minLvl))
		{
			return false;
		}
		int minPvps = instance.getConfigInt("minPvps");
		int maxPvps = instance.getConfigInt("maxPvps");
		if ((player.getPvpKills() < minPvps) || ((maxPvps != -1) && (player.getPvpKills() > maxPvps)))
		{
			return false;
		}
		player.sendMessage(LanguageEngine.getMsg("event_choosingInstance", new Object[]
		{
			instance.getName()
		}));
		return true;
	}
	
	@Override
	public void playerWentAfk(PlayerEventInfo player, boolean warningOnly, int afkTime)
	{
		if (warningOnly)
		{
			player.sendMessage(LanguageEngine.getMsg("event_afkWarning", new Object[]
			{
				Integer.valueOf(PlayerEventInfo.AFK_WARNING_DELAY / 1000),
				Integer.valueOf(PlayerEventInfo.AFK_KICK_DELAY / 1000)
			}));
		}
		else if (afkTime == 0)
		{
			player.sendMessage(LanguageEngine.getMsg("event_afkMarked"));
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: player " + player.getPlayersName() + " has just gone afk");
			}
		}
		else if ((afkTime % 60) == 0)
		{
			player.sendMessage(LanguageEngine.getMsg("event_afkDurationInfo", new Object[]
			{
				Integer.valueOf(afkTime / 60)
			}));
		}
		if (player.isTitleUpdated())
		{
			player.setTitle(getTitle(player), true);
			player.broadcastTitleInfo();
		}
	}
	
	@Override
	public void playerReturnedFromAfk(PlayerEventInfo player)
	{
		if (player.isTitleUpdated())
		{
			player.setTitle(getTitle(player), true);
			player.broadcastTitleInfo();
		}
	}
	
	@Override
	public boolean addDisconnectedPlayer(PlayerEventInfo player, EventManager.DisconnectedPlayerData data)
	{
		boolean added = false;
		if (data != null)
		{
			if ((_rejoinEventAfterDisconnect) && (_manager.getState() == MainEventManager.State.RUNNING))
			{
				AbstractEventInstance instance = getMatch(data.getInstance());
				if ((instance != null) && (instance.isActive()))
				{
					EventTeam team = data.getTeam();
					if (team != null)
					{
						player.sendMessage(LanguageEngine.getMsg("registering_afterDisconnect_true"));
						
						player.setIsRegisteredToMainEvent(true, getEventType());
						synchronized (_manager.getPlayers())
						{
							_manager.getPlayers().add(player);
						}
						player.onEventStart(this);
						(_teams.get(Integer.valueOf(instance.getInstance().getId()))).get(Integer.valueOf(team.getTeamId())).addPlayer(player, true);
						
						prepareDisconnectedPlayer(player);
						
						respawnPlayer(player, instance.getInstance().getId());
						if (_removeWarningAfterReconnect)
						{
							EventWarnings.getInstance().removeWarning(player, 1);
						}
					}
				}
			}
			else
			{
				player.sendMessage(LanguageEngine.getMsg("registering_afterDisconnect_false"));
			}
		}
		return added;
	}
	
	protected void prepareDisconnectedPlayer(PlayerEventInfo player)
	{
		boolean removeBuffs = getBoolean("removeBuffsOnStart");
		if (removeBuffs)
		{
			player.removeBuffs();
		}
		if (_allowSchemeBuffer)
		{
			EventBuffer.getInstance().buffPlayer(player, true);
		}
		if (_removePartiesOnStart)
		{
			PartyData pt = player.getParty();
			if (pt != null)
			{
				player.getParty().removePartyMember(player);
			}
		}
		if (player.isTitleUpdated())
		{
			player.setTitle(getTitle(player), true);
		}
	}
	
	@Override
	public void onDisconnect(PlayerEventInfo player)
	{
		if (player.isOnline())
		{
			if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: player " + player.getPlayersName() + " (instance id = " + player.getInstanceId() + ") disconnecting from the event");
			}
			if (_spectators != null)
			{
				if (_spectators.contains(player))
				{
					synchronized (_spectators)
					{
						_spectators.remove(player);
					}
					player.setInstanceId(0);
					player.setXYZInvisible(player.getOrigLoc().getX(), player.getOrigLoc().getY(), player.getOrigLoc().getZ());
				}
			}
			EventTeam team = player.getEventTeam();
			EventPlayerData playerData = player.getEventData();
			player.restoreData();
			player.setXYZInvisible(player.getOrigLoc().getX(), player.getOrigLoc().getY(), player.getOrigLoc().getZ());
			
			EventWarnings.getInstance().addPoints(player.getPlayersId(), 1);
			
			boolean running = false;
			boolean allowRejoin = true;
			
			AbstractEventInstance playersMatch = getMatch(player.getInstanceId());
			if (playersMatch == null)
			{
				NexusLoader.debug("Player's EventInstance is null, called onDisconnect", Level.WARNING);
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: !!! -.- player's EVENT INSTANCE is null after calling onDisconnect. Player's instanceId is = " + player.getInstanceId());
				}
				running = false;
			}
			else
			{
				running = playersMatch.isActive();
			}
			team.removePlayer(player);
			_manager.getPlayers().remove(player);
			
			CallBack.getInstance().getPlayerBase().playerDisconnected(player);
			if (running)
			{
				if (NexusLoader.detailedDebug)
				{
					print("AbstractMainEvent: -.- event is active");
				}
				debug(getEventName() + ": Player " + player.getPlayersName() + " disconnected from " + getEventName() + " event.");
				if (team.getPlayers().isEmpty())
				{
					announce(player.getInstanceId(), LanguageEngine.getMsg("event_disconnect_team", new Object[]
					{
						team.getTeamName()
					}));
					allowRejoin = false;
					
					debug(getEventName() + ": all players from team " + team.getTeamName() + " have disconnected.");
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: ALL PLAYERS FROM TEAM " + team.getTeamName() + " disconnected");
					}
				}
				if (!checkIfEventCanContinue(player.getInstanceId(), player))
				{
					announce(player.getInstanceId(), LanguageEngine.getMsg("event_disconnect_all"));
					endInstance(player.getInstanceId(), true, false, false);
					allowRejoin = false;
					
					debug(getEventName() + ": no players left in the teams, the fight can't continue. The event has been aborted!");
					if (NexusLoader.detailedDebug)
					{
						print("AbstractMainEvent: NO PLAYERS LEFT IN THE TEAMS, THE FIGHT CAN'T CONTINUE! (checkIfEventCanContinue = false)");
					}
					return;
				}
				if ((allowRejoin) && (allowsRejoinOnDisconnect()))
				{
					EventManager.getInstance().addDisconnectedPlayer(player, team, playerData, this);
				}
			}
			else if (NexusLoader.detailedDebug)
			{
				print("AbstractMainEvent: -.- event IS NOT active anymore");
			}
		}
	}
	
	protected boolean checkIfEventCanContinue(int instanceId, PlayerEventInfo disconnectedPlayer)
	{
		int teamsOn = 0;
		for (EventTeam team : (_teams.get(instanceId)).values())
		{
			for (PlayerEventInfo pi : team.getPlayers())
			{
				if ((pi != null) && (pi.isOnline()))
				{
					teamsOn++;
					break;
				}
			}
		}
		return teamsOn >= 2;
	}
	
	@Override
	public boolean canUseItem(PlayerEventInfo player, ItemData item)
	{
		if ((notAllovedItems != null) && (Arrays.binarySearch(notAllovedItems, item.getItemId()) >= 0))
		{
			player.sendMessage(LanguageEngine.getMsg("event_itemNotAllowed"));
			return false;
		}
		if ((item.isPotion()) && (!getBoolean("allowPotions")))
		{
			return false;
		}
		if (item.isScroll())
		{
			return false;
		}
		if ((item.isPetCollar()) && (!_allowPets))
		{
			player.sendMessage(LanguageEngine.getMsg("event_petsNotAllowed"));
			return false;
		}
		return true;
	}
	
	@Override
	public boolean canDestroyItem(PlayerEventInfo player, ItemData item)
	{
		return true;
	}
	
	@Override
	public void onItemUse(PlayerEventInfo player, ItemData item)
	{
	}
	
	@Override
	public boolean canUseSkill(PlayerEventInfo player, SkillData skill)
	{
		if ((notAllovedSkillls != null) && (Arrays.binarySearch(notAllovedSkillls, skill.getId()) >= 0))
		{
			player.sendMessage(LanguageEngine.getMsg("event_skillNotAllowed"));
			return false;
		}
		if (skill.getSkillType().equals("RESURRECT"))
		{
			return false;
		}
		if (skill.getSkillType().equals("RECALL"))
		{
			return false;
		}
		if (skill.getSkillType().equals("SUMMON_FRIEND"))
		{
			return false;
		}
		if (skill.getSkillType().equals("FAKE_DEATH"))
		{
			return false;
		}
		if ((!_allowSummons) && (skill.getSkillType().equals("SUMMON")))
		{
			player.sendMessage(LanguageEngine.getMsg("event_summonsNotAllowed"));
			return false;
		}
		return true;
	}
	
	@Override
	public void onSkillUse(final PlayerEventInfo player, SkillData skill)
	{
		if ((skill.getSkillType() != null) && (skill.getSkillType().equals("SUMMON")))
		{
			CallBack.getInstance().getOut().scheduleGeneral(() -> EventBuffer.getInstance().buffPet(player), 2000L);
		}
	}
	
	@Override
	public boolean canSupport(PlayerEventInfo player, CharacterData target)
	{
		if ((target.getEventInfo() == null) || (target.getEventInfo().getEvent() != player.getEvent()))
		{
			return false;
		}
		if (target.getEventInfo().getTeamId() == player.getTeamId())
		{
			return true;
		}
		return false;
	}
	
	@Override
	public boolean canAttack(PlayerEventInfo player, CharacterData target)
	{
		if (target.getEventInfo() == null)
		{
			return true;
		}
		if (target.getEventInfo().getEvent() != player.getEvent())
		{
			return false;
		}
		if (target.getEventInfo().getTeamId() != player.getTeamId())
		{
			return true;
		}
		return false;
	}
	
	@Override
	public boolean onAttack(CharacterData cha, CharacterData target)
	{
		return true;
	}
	
	@Override
	public boolean onSay(PlayerEventInfo player, String text, int channel)
	{
		if (text.equals(".scheme"))
		{
			EventManager.getInstance().getHtmlManager().showSelectSchemeForEventWindow(player, "none", getEventType().getAltTitle());
			return false;
		}
		return true;
	}
	
	@Override
	public boolean onNpcAction(PlayerEventInfo player, NpcData npc)
	{
		return false;
	}
	
	@Override
	public void onDamageGive(CharacterData cha, CharacterData target, int damage, boolean isDOT)
	{
	}
	
	@Override
	public void onKill(PlayerEventInfo player, CharacterData target)
	{
	}
	
	@Override
	public void onDie(PlayerEventInfo player, CharacterData killer)
	{
	}
	
	@Override
	public boolean canInviteToParty(PlayerEventInfo player, PlayerEventInfo target)
	{
		if (target.getEvent() != player.getEvent())
		{
			return false;
		}
		if ((!player.canInviteToParty()) || (!target.canInviteToParty()))
		{
			return false;
		}
		if (target.getTeamId() == player.getTeamId())
		{
			return true;
		}
		return false;
	}
	
	@Override
	public boolean canTransform(PlayerEventInfo player)
	{
		return true;
	}
	
	@Override
	public boolean canBeDisarmed(PlayerEventInfo player)
	{
		return true;
	}
	
	@Override
	public int allowTransformationSkill(PlayerEventInfo playerEventInfo, SkillData skillData)
	{
		return 0;
	}
	
	@Override
	public boolean canSaveShortcuts(PlayerEventInfo player)
	{
		return true;
	}
	
	public boolean isInEvent(CharacterData ch)
	{
		return false;
	}
	
	public boolean allowKill(CharacterData target, CharacterData killer)
	{
		return true;
	}
	
	public boolean allowsRejoinOnDisconnect()
	{
		return true;
	}
	
	@SuppressWarnings("unused")
	protected void clockTick() throws Exception
	{
	}
}
