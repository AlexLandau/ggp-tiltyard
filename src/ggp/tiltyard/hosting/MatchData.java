package ggp.tiltyard.hosting;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;

import org.ggp.galaxy.shared.game.Game;
import org.ggp.galaxy.shared.match.Match;
import org.ggp.galaxy.shared.match.MatchPublisher;
import org.ggp.galaxy.shared.persistence.Persistence;
import org.ggp.galaxy.shared.statemachine.MachineState;
import org.ggp.galaxy.shared.statemachine.Move;
import org.ggp.galaxy.shared.statemachine.StateMachine;
import org.ggp.galaxy.shared.statemachine.exceptions.GoalDefinitionException;
import org.ggp.galaxy.shared.statemachine.implementation.prover.ProverStateMachine;
import org.json.JSONException;
import org.json.JSONObject;

@PersistenceCapable
public class MatchData {	
    // NOTE: matchKey is the unique identifier under which this match has been
    // published to the http://matches.ggp.org/ spectator server. The complete
    // match can thus be found at http://matches.ggp.org/matches/X/ where X is
    // the matchKey.
    @PrimaryKey @Persistent private String matchKey;

    @SuppressWarnings("unused")
	@Persistent private String[] playerURLs;
    @Persistent private String[] pendingMoves;
    @Persistent private Text theGameJSON;
    @Persistent private Text theMatchJSON;
    @Persistent private String theAuthToken;
    
    private Match theMatch;
    
    public MatchData(String matchId, List<String> playerNames, List<String> playerURLs, int startClock, int playClock, Game theGame) throws IOException {        
        try {
            JSONObject theSerializedGame = new JSONObject(theGame.serializeToJSON());            
            theSerializedGame.remove("theStylesheet");
            theSerializedGame.remove("theDescription");
            theGameJSON = new Text(theSerializedGame.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        theMatch = new Match(matchId, startClock, playClock, theGame);
        theMatch.setCryptographicKeys(StoredCryptoKeys.loadCryptoKeys("Artemis"));
        this.playerURLs = playerURLs.toArray(new String[]{});
        //theMatch.setPlayerNamesFromHost(playerNames);

        // NOTE: This code assumes that the first state for the match will always have
        // a non-forced move for at least one player. If this is not the case, it will
        // need to be updated, since we shouldn't actually begin the match in the state
        // we get from getInitialState().
        StateMachine theMachine = getMyStateMachine();
        MachineState theState = theMachine.getInitialState();
        this.setState(theMachine, theState, null);        

        matchKey = publish();
        save();
    }

    public String getMatchKey() {
        return matchKey;
    }

    public String[] getPendingMoves() {
        return pendingMoves;
    }

    public void setState(StateMachine theMachine, MachineState state, List<Move> moves) throws IOException {
        theMatch.appendState(state.getContents());
        theMatch.appendNoErrors();
        if (moves != null) {
            theMatch.appendMoves2(moves);            
        }
        if (theMachine.isTerminal(state)) {
            try {
                theMatch.markCompleted(theMachine.getGoals(state));
            } catch (GoalDefinitionException e) {
                throw new IOException(e);
            }
        }

        try {
            // Clear the current pending moves
            pendingMoves = new String[theMachine.getRoles().size()];

            // If the match isn't completed, we should fill in all of the moves
            // that are automatically forced (because the player has no other move).
            if (!theMatch.isCompleted()) {
                for (int i = 0; i < pendingMoves.length; i++) {
                    if (theMachine.getLegalMoves(state, theMachine.getRoles().get(i)).size() == 1) {
                        pendingMoves[i] = theMachine.getLegalMoves(state, theMachine.getRoles().get(i)).get(0).toString();
                    }
                }
            }
        } catch(Exception e) {
            throw new IOException(e);
        }
    }    

    public MachineState getState(StateMachine theMachine) throws IOException {
        if (theMatch.getMostRecentState() == null)
            return null;

        try {
            return theMachine.getMachineStateFromSentenceList(theMatch.getMostRecentState());
        } catch(Exception e) {
            throw new IOException(e);
        }
    }

    public void setPendingMove(int nRoleIndex, String move) {
        this.pendingMoves[nRoleIndex] = move;
    }

    public boolean allPendingMovesSubmitted() {
        for (int i = 0; i < pendingMoves.length; i++) {
            if (pendingMoves[i] == null) return false;
        }
        return true;
    }

    public StateMachine getMyStateMachine() throws IOException {
        StateMachine theMachine = new ProverStateMachine();
        theMachine.initialize(Game.loadFromJSON(theGameJSON.getValue()).getRules());
        return theMachine;
    }

    public static final String SPECTATOR_SERVER = "http://matches.ggp.org/";
    public String publish() throws IOException {
        return MatchPublisher.publishToSpectatorServer(SPECTATOR_SERVER, theMatch);
    }

    private void deflateForSaving() {
        theMatchJSON = new Text(theMatch.toJSON());
        theAuthToken = theMatch.getSpectatorAuthToken();
    }

    private void inflateAfterLoading() {
        try {
            theMatch = new Match(theMatchJSON.getValue(), Game.loadFromJSON(theGameJSON.getValue()), theAuthToken);
            theMatch.setCryptographicKeys(StoredCryptoKeys.loadCryptoKeys("Artemis"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        deflateForSaving();
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();
    }    

    /* Static accessor methods */
    public static Set<MatchData> loadMatches() throws IOException {
        Set<MatchData> s = Persistence.loadAll(MatchData.class);
        for (MatchData m : s) {
            if (m != null) m.inflateAfterLoading();
        }
        return s;
    }

    public static MatchData loadMatchData(String matchKey) throws IOException {
        MatchData m = Persistence.loadSpecific(matchKey, MatchData.class);
        if (m != null) m.inflateAfterLoading();
        return m;
    }    
}