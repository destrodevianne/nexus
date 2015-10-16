/*
 * Decompiled with CFR 0_102.
 * 
 * Could not load the following classes:
 *  cz.nxs.interf.NexusOut
 *  cz.nxs.interf.PlayerEventInfo
 *  cz.nxs.interf.delegate.CharacterData
 *  cz.nxs.interf.delegate.InstanceData
 *  cz.nxs.interf.delegate.PartyData
 *  javolution.text.TextBuilder
 *  javolution.util.FastMap
 */
package cz.nxs.events.engine.main.events;

import cz.nxs.events.EventGame;
import cz.nxs.events.engine.base.ConfigModel;
import cz.nxs.events.engine.base.EventPlayerData;
import cz.nxs.events.engine.base.EventSpawn;
import cz.nxs.events.engine.base.EventType;
import cz.nxs.events.engine.base.Loc;
import cz.nxs.events.engine.base.PvPEventPlayerData;
import cz.nxs.events.engine.main.MainEventManager;
import cz.nxs.events.engine.main.base.IEventInstance;
import cz.nxs.events.engine.main.base.MainEventInstanceType;
import cz.nxs.events.engine.main.events.AbstractMainEvent;
import cz.nxs.events.engine.mini.SpawnType;
import cz.nxs.events.engine.team.EventTeam;
import cz.nxs.interf.NexusOut;
import cz.nxs.interf.PlayerEventInfo;
import cz.nxs.interf.delegate.CharacterData;
import cz.nxs.interf.delegate.InstanceData;
import cz.nxs.interf.delegate.PartyData;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import javolution.text.TextBuilder;
import javolution.util.FastMap;

public class TvT
extends AbstractMainEvent {
    private FastMap<Integer, EventInstance> _matches;
    private boolean _waweRespawn;
    private int _teamsCount;

    public TvT(EventType type, MainEventManager manager) {
        super(type, manager);
        this.setRewardTypes(new String[]{"Winner", "Loser", "Tie"});
        this._configs.put((Object)"killsForReward", (Object)new ConfigModel("killsForReward", "0", "The minimum kills count required to get a reward (includes all possible rewards)."));
        this._configs.put((Object)"resDelay", (Object)new ConfigModel("resDelay", "15", "The delay after which the player is resurrected. In seconds."));
        this._configs.put((Object)"waweRespawn", (Object)new ConfigModel("waweRespawn", "true", "Enables the wawe-style respawn system.", ConfigModel.InputType.Boolean));
        this._configs.put((Object)"createParties", (Object)new ConfigModel("createParties", "true", "Put 'True' if you want this event to automatically create parties for players in each team.", ConfigModel.InputType.Boolean));
        this._configs.put((Object)"maxPartySize", (Object)new ConfigModel("maxPartySize", "10", "The maximum size of party, that can be created. Works only if <font color=LEVEL>createParties</font> is true."));
        this._configs.put((Object)"teamsCount", (Object)new ConfigModel("teamsCount", "2", "The ammount of teams in the event. Max is 5."));
        this._instanceTypeConfigs.put((Object)"teamsCount", (Object)new ConfigModel("teamsCount", "2", "You may specify the count of teams only for this instance. This config overrides event's default teams ammount."));
    }

    @Override
    public void initEvent() {
        super.initEvent();
        this._waweRespawn = this.getBoolean("waweRespawn");
        if (this._waweRespawn) {
            this.initWaweRespawns(this.getInt("resDelay"));
        }
        this._runningInstances = 0;
    }

    protected int initInstanceTeams(MainEventInstanceType type) {
        this._teamsCount = type.getConfigInt("teamsCount");
        if (this._teamsCount < 2 || this._teamsCount > 5) {
            this._teamsCount = this.getInt("teamsCount");
        }
        if (this._teamsCount < 2 || this._teamsCount > 5) {
            this._teamsCount = 2;
        }
        this.createTeams(this._teamsCount, type.getInstance().getId());
        return this._teamsCount;
    }

    @Override
    public void runEvent() {
        if (!this.dividePlayers()) {
            this.clearEvent();
            return;
        }
        if (this.getBoolean("createParties")) {
            this.createParties(this.getInt("maxPartySize"));
        }
        this._matches = new FastMap();
        for (InstanceData instance : this._instances) {
            EventInstance match = new EventInstance(instance);
            this._matches.put((Object)instance.getId(), (Object)match);
            ++this._runningInstances;
            match.scheduleNextTask(0);
        }
    }

    @Override
    public void onEventEnd() {
        int minKills = this.getInt("killsForReward");
        this.rewardAllTeams(-1, 0, minKills);
    }

    @Override
    protected synchronized boolean instanceEnded() {
        --this._runningInstances;
        if (this._runningInstances == 0) {
            this._manager.end();
            return true;
        }
        return false;
    }

    protected synchronized void endInstance(int instance, boolean canBeAborted) {
        ((EventInstance)this._matches.get((Object)instance)).setNextState(EventState.END);
        if (canBeAborted) {
            ((EventInstance)this._matches.get((Object)instance)).setCanBeAborted();
        }
        ((EventInstance)this._matches.get((Object)instance)).getClock().setTime(0);
    }

    @Override
    protected String getScorebar(int instance) {
        int count = ((FastMap)this._teams.get((Object)instance)).size();
        TextBuilder tb = new TextBuilder();
        for (EventTeam team : ((FastMap)this._teams.get((Object)instance)).values()) {
            if (count <= 4) {
                tb.append(team.getTeamName() + ": " + team.getScore() + "  ");
                continue;
            }
            tb.append(team.getTeamName().substring(0, 1) + ": " + team.getScore() + "  ");
        }
        if (count <= 3) {
            tb.append("Time: " + ((EventInstance)this._matches.get((Object)instance)).getClock().getTime());
        }
        return tb.toString();
    }

    @Override
    protected String getTitle(PlayerEventInfo pi) {
        if (pi.isAfk()) {
            return "AFK";
        }
        return "Kills: " + this.getEventData(pi).getScore() + " Deaths: " + this.getEventData(pi).getDeaths();
    }

    @Override
    public void onKill(PlayerEventInfo player, CharacterData target) {
        if (target.getEventInfo() == null) {
            return;
        }
        if (player.getTeamId() != target.getEventInfo().getTeamId()) {
            player.getEventTeam().raiseScore(1);
            player.getEventTeam().raiseKills(1);
            this.getEventData(player).raiseScore(1);
            this.getEventData(player).raiseKills(1);
            if (player.isTitleUpdated()) {
                player.setTitle(this.getTitle(player), true);
                player.broadcastTitleInfo();
            }
            this.setScoreStats(player, this.getEventData(player).getScore());
            this.setKillsStats(player, this.getEventData(player).getKills());
        }
    }

    @Override
    public void onDie(PlayerEventInfo player, CharacterData killer) {
        this.getEventData(player).raiseDeaths(1);
        this.setDeathsStats(player, this.getEventData(player).getDeaths());
        if (this._waweRespawn) {
            this._waweScheduler.addPlayer(player);
        } else {
            this.scheduleRevive(player, this.getInt("resDelay") * 1000);
        }
    }

    public EventPlayerData createEventData(PlayerEventInfo player) {
        PvPEventPlayerData d = new PvPEventPlayerData(player, (EventGame)this);
        return d;
    }

    public PvPEventPlayerData getEventData(PlayerEventInfo player) {
        return (PvPEventPlayerData)player.getEventData();
    }

    @Override
    public synchronized void clearEvent(int instanceId) {
        if (this._matches != null) {
            for (EventInstance match : this._matches.values()) {
                if (instanceId != 0 && instanceId != match.getInstance().getId()) continue;
                match.abort();
            }
        }
        for (PlayerEventInfo player : this.getPlayers(instanceId)) {
            if (!player.isOnline()) continue;
            if (player.isParalyzed()) {
                player.setIsParalyzed(false);
            }
            if (player.isImmobilized()) {
                player.unroot();
            }
            player.setInstanceId(0);
            player.restoreData();
            player.teleport(player.getOrigLoc(), 0, true, 0);
            player.sendMessage("You're being teleported back to you previous location.");
            if (player.getParty() != null) {
                PartyData party = player.getParty();
                party.removePartyMember(player);
            }
            player.broadcastUserInfo();
        }
        this.clearPlayers(true, instanceId);
    }

    @Override
    public synchronized void clearEvent() {
        this.clearEvent(0);
    }

    @Override
    protected void respawnPlayer(PlayerEventInfo pi, int instance) {
        EventSpawn spawn = this.getSpawn(SpawnType.Regular, pi.getTeamId());
        if (spawn != null) {
            Loc loc = new Loc(spawn.getLoc().getX(), spawn.getLoc().getY(), spawn.getLoc().getZ());
            loc.addRadius(spawn.getRadius());
            pi.teleport(loc, 0, true, pi.getInstanceId());
            pi.sendMessage("You've been respawned.");
        } else {
            this.debug("Error on respawnPlayer - no spawn type REGULAR, team " + pi.getTeamId() + " has been found. Event aborted.");
        }
    }

    @Override
    public String getEstimatedTimeLeft() {
        if (this._matches == null) {
            return "Starting";
        }
        Iterator i$ = this._matches.values().iterator();
        if (i$.hasNext()) {
            EventInstance match = (EventInstance)i$.next();
            return match.getClock().getTime();
        }
        return null;
    }

    protected IEventInstance getMatch(int instanceId) {
        return (IEventInstance)this._matches.get((Object)instanceId);
    }

    private static enum EventState {
        START,
        FIGHT,
        END,
        TELEPORT,
        INACTIVE;
        

        private EventState() {
        }
    }

    private class EventInstance
    implements Runnable,
    IEventInstance {
        private InstanceData _instance;
        private EventState _state;
        private AbstractMainEvent.Clock _clock;
        private boolean _canBeAborted;
        private ScheduledFuture<?> _task;

        private EventInstance(InstanceData instance) {
            this._canBeAborted = false;
            this._task = null;
            this._instance = instance;
            this._state = EventState.START;
            this._clock = new AbstractMainEvent.Clock((AbstractMainEvent)TvT.this, (IEventInstance)this);
        }

        protected void setNextState(EventState state) {
            this._state = state;
        }

        public void setCanBeAborted() {
            this._canBeAborted = true;
        }

        @Override
        public boolean isActive() {
            return this._state != EventState.INACTIVE;
        }

        @Override
        public InstanceData getInstance() {
            return this._instance;
        }

        @Override
        public AbstractMainEvent.Clock getClock() {
            return this._clock;
        }

        @Override
        public ScheduledFuture<?> scheduleNextTask(int time) {
            if (time > 0) {
                this._task = NexusOut.scheduleGeneral((Runnable)this, (long)time);
            } else {
                NexusOut.executeTask((Runnable)this);
            }
            return this._task;
        }

        public void abort() {
            if (this._task != null) {
                this._task.cancel(false);
            }
            this._clock.abort();
        }

        @Override
        public void run() {
            try {
                switch (this._state) {
                    case START: {
                        TvT.this.teleportPlayers(this._instance.getId(), SpawnType.Regular, false);
                        TvT.this.preparePlayers(this._instance.getId());
                        TvT.this.forceSitAll(this._instance.getId());
                        this.setNextState(EventState.FIGHT);
                        this.scheduleNextTask(10000);
                        break;
                    }
                    case FIGHT: {
                        TvT.this.forceStandAll(this._instance.getId());
                        this.setNextState(EventState.END);
                        this._clock.startClock(TvT.this._manager.getRunTime());
                        break;
                    }
                    case END: {
                        this._clock.setTime(0);
                        this.setNextState(EventState.INACTIVE);
                        if (TvT.this.instanceEnded() || !this._canBeAborted) break;
                        TvT.this.clearEvent(this._instance.getId());
                    }
                }
            }
            catch (Throwable e) {
                e.printStackTrace();
                TvT.this._manager.endDueToError("An error in the Event Engine occured. The event has been aborted.");
            }
        }
    }

}

