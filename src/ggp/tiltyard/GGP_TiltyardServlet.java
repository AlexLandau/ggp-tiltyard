package ggp.tiltyard;

import ggp.tiltyard.players.Registration;
import ggp.tiltyard.scheduling.Scheduling;
import ggp.tiltyard.scheduling.TournamentData;
import ggp.tiltyard.scheduling.Tournaments;
import ggp.tiltyard.backends.BackendRegistration;
import ggp.tiltyard.hosting.Hosting;
import ggp.tiltyard.identity.GitkitIdentity;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.http.*;

import org.ggp.base.util.loader.RemoteResourceLoader;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;

@SuppressWarnings("serial")
public class GGP_TiltyardServlet extends HttpServlet {
    public static boolean isDatastoreWriteable() {
        CapabilitiesService service = CapabilitiesServiceFactory.getCapabilitiesService();
        CapabilityStatus status = service.getStatus(Capability.DATASTORE_WRITE).getStatus();
        return (status != CapabilityStatus.DISABLED);
    }
    
    public static boolean isDatastoreReadable() {
        CapabilitiesService service = CapabilitiesServiceFactory.getCapabilitiesService();
        CapabilityStatus status = service.getStatus(Capability.DATASTORE).getStatus();
        return (status != CapabilityStatus.DISABLED);
    }

    public static void setAccessControlHeader(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Access-Control-Allow-Age", "86400");
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setAccessControlHeader(resp);
        resp.setCharacterEncoding("utf-8");

        if (req.getRequestURI().equals("/oauth2callback")) {
        	GitkitIdentity.handleOauthCallback(req, resp);
        	return;
        }
        
        if (req.getRequestURI().startsWith("/identity/")) {        	
        	GitkitIdentity.doGet(req.getRequestURI().replaceFirst("/identity/", ""), req, resp);
        	return;
        }
        
        if (req.getRequestURI().equals("/cron/scheduling_round")) {
            if (isDatastoreWriteable()) {
                Scheduling.runSchedulingRound();            
                resp.setContentType("text/plain");
                resp.getWriter().println("Starting scheduling round.");
            }
            resp.setStatus(200);
            return;
        }
        
        if (req.getRequestURI().startsWith("/data/tournaments/")) {
        	String tournamentKey = req.getRequestURI().replaceFirst("/data/tournaments/", "");
        	TournamentData t = TournamentData.loadTournamentData(tournamentKey);
            resp.setContentType("text/plain");
            resp.getWriter().println(t.getDisplayData());
            resp.setStatus(200);
            return;
        }
        
        /* Can be enabled to push a tournament YAML descriptor.
        if (req.getRequestURI().startsWith("/push_tournament")) {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("tiltyardOpen2016.yaml"), "utf-8"));
            StringBuffer response = new StringBuffer();

            String line;
            while( (line = br.readLine()) != null ) {
                response.append(line + "\n");
            }
            
            br.close();

            resp.setContentType("text/plain");
            resp.getWriter().println(response.toString());
            resp.setStatus(200);
            new TournamentData("tiltyard_open_20161209", response.toString()).save();
        	return;
        }
        */

        if (req.getRequestURI().startsWith("/data/")) {
            Registration.doGet(req.getRequestURI().replaceFirst("/data/", ""), req, resp);
            return;
        }

        if (req.getRequestURI().startsWith("/scheduling/")) {
        	Scheduling.doGet(req.getRequestURI().replace("/scheduling/", ""), resp);
        	return;
        }        
        
        if (req.getRequestURI().startsWith("/hosting/tasks/")) {
        	Hosting.doTask(req, resp);
        	return;
        }

        // TODO: Clean this up so that Tiltyard has a native interface for uploading
        //       tournament YAML descriptions and creating tournaments based on them.
        if (req.getRequestURI().startsWith("/create/tournament=")) {
        	String tournament_key = req.getRequestURI().replace("/create/tournament=", "");
        	if (TournamentData.loadTournamentData(tournament_key) != null) {
        		resp.getWriter().println("Tournament " + tournament_key + " already exists.");
        	} else {
	        	String yaml = RemoteResourceLoader.loadRaw("https://storage.googleapis.com/ggp-static-content/" + tournament_key + ".yaml");
	            resp.setContentType("text/plain");
	        	new TournamentData(tournament_key, yaml).save();
	            resp.getWriter().println("Started tourney " + tournament_key + " from spec:\n\n" + yaml);
        	}
    		resp.setStatus(200);
    		return;        	
        }
        
        String reqURI = req.getRequestURI();
        if (reqURI.equals("/about")) reqURI += "/";
        if (reqURI.equals("/players")) reqURI += "/";
        if (reqURI.equals("/hosting")) reqURI += "/";
        if (reqURI.equals("/tournaments")) reqURI += "/";
        if (reqURI.endsWith("/")) {
            reqURI += "index.html";
        }

        // TODO: Tighten these up so they only return valid pages for a specific,
        // well-constrained URL structure.
        if (reqURI.startsWith("/players/") && !reqURI.equals("/players/index.html")) {
            reqURI = "/players/playerPage.html";
        }
        if (reqURI.startsWith("/players-test/")) {
            reqURI = "/players/playerPageTest.html";
        }
        if (reqURI.startsWith("/tournaments/") && !reqURI.equals("/tournaments/index.html")) {
        	reqURI = "/tournaments/tournamentPage.html";
        }        
        if (reqURI.startsWith("/hosting/") && !reqURI.equals("/hosting/index.html")) {
        	reqURI = "/hosting/humanUI.html";
        }

        boolean writeAsBinary = false;        
        if (reqURI.endsWith(".html")) {
            resp.setContentType("text/html");
        } else if (reqURI.endsWith(".xml")) {
            resp.setContentType("application/xml");
        } else if (reqURI.endsWith(".xsl")) {
            resp.setContentType("application/xml");
        } else if (reqURI.endsWith(".js")) {
            resp.setContentType("text/javascript");   
        } else if (reqURI.endsWith(".json")) {
            resp.setContentType("text/javascript");
        } else if (reqURI.endsWith(".png")) {
            resp.setContentType("image/png");
            writeAsBinary = true;
        } else if (reqURI.endsWith(".ico")) {
            resp.setContentType("image/png");
            writeAsBinary = true;
        } else {
            resp.setContentType("text/plain");
        }
        
        try {
            if (writeAsBinary) {
                writeStaticBinaryPage(resp, reqURI.substring(1));
            } else {
                // Temporary limits on caching, for during development.
                resp.setHeader("Cache-Control", "no-cache");
                resp.setHeader("Pragma", "no-cache");
                writeStaticTextPage(resp, reqURI.substring(1));
            }
        } catch(IOException e) {
            resp.setStatus(404);
        }
    }
    
    public static final String readDataFromRequest(HttpServletRequest req) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(req.getInputStream()));
        int contentLength = Integer.parseInt(req.getHeader("Content-Length").trim());
        StringBuilder theInput = new StringBuilder();
        for (int i = 0; i < contentLength; i++) {
            theInput.append((char)br.read());
        }
        return theInput.toString().trim();
    }

    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {        
        setAccessControlHeader(resp);
        resp.setHeader("Access-Control-Allow-Origin", "tiltyard.ggp.org");

        if (req.getRequestURI().equals("/oauth2callback")) {
        	GitkitIdentity.handleOauthCallback(req, resp);
        	return;
        }
        
        if (req.getRequestURI().startsWith("/hosting/tasks/")) {
        	Hosting.doTask(req, resp);
        	return;
        }
        
        String theURI = req.getRequestURI();
        String in = null;
        if (!theURI.contains("/data/uploadPlayerImage/")) {
        	in = readDataFromRequest(req);
        }
        
        if (!isDatastoreReadable()) {
        	// TODO(schreib): Come up with a better solution for this, if it happens.
        	throw new RuntimeException("Got incoming POST and datastore was not readable!");
        } else if (theURI.startsWith("/hosting/")) {
        	// Handle any match hosting callbacks even if the datastore isn't writeable,
        	// as long as the datastore is readable; these just add tasks to the task queue
        	// and the actual writing is done in the tasks, which will be retried until the
        	// datastore is accepting writes again.
        	Hosting.doPost(theURI.replace("/hosting/", ""), in, resp);
        } else if (theURI.startsWith("/scheduling/")) {
        	// TODO(schreib): Figure out a story for the scheduling servlet when the
        	// datastore is no longer writeable.
        	Scheduling.doPost(theURI.replace("/scheduling/", ""), in, resp);
        } else if (!isDatastoreWriteable()) {
        	// For player registration or backend registration, just drop
        	// incoming requests when the datastore isn't writeable.        	
        } else if (theURI.startsWith("/backends/")) {        	
            BackendRegistration.doPost(theURI.replace("/backends/", ""), in, req.getRemoteAddr(), resp);
        } else if (theURI.startsWith("/tournaments/")) {
            Tournaments.doPost(theURI.replace("/tournaments/", ""), in, req, resp);
        } else {
            Registration.doPost(theURI, in, req, resp);
        }
    }

    public void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {  
        setAccessControlHeader(resp);
    }

    /* --- */

    public static void writeStaticTextPage(HttpServletResponse resp, String theURI) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(theURI), "utf-8"));
        StringBuffer response = new StringBuffer();

        String line;
        while( (line = br.readLine()) != null ) {
            response.append(line + "\n");
        }
        
        br.close();        

        resp.getWriter().println(response.toString());
    }

    public static void writeStaticBinaryPage(HttpServletResponse resp, String theURI) throws IOException {
        InputStream in = new FileInputStream(theURI);
        byte[] buf = new byte[1024];
        while (in.read(buf) > 0) {
            resp.getOutputStream().write(buf);
        }
        in.close();        
    }    
}