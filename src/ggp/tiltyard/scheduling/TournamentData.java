package ggp.tiltyard.scheduling;

import net.alloyggp.tournament.api.Tournament;

@PersistenceCapable
public class TournamentData {
    @PrimaryKey @Persistent private String internalName;

    //TODO: Loaded from a YAML file or string via TournamentSpecParser
    private Tournament tournament;

    @Persistent private String persistedSeeding = null;

    public String getPersistedSeeding() {
        return persistedSeeding;
    }

    public void setPersistedSeeding(String persistedSeeding) {
        this.persistedSeeding = persistedSeeding;
        //TODO: Persist this
    }

    public Tournament getTournament() {
        return tournament;
    }
}
