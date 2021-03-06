// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io.imagery;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.GeorefImage.State;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.projection.Mercator;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.WMSLayer;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.io.ProgressInputStream;
import org.openstreetmap.josm.tools.Utils;


public class WMSGrabber extends Grabber {

    protected String baseURL;
    private final boolean urlWithPatterns;
    private List<String> serverProjections;
    private Map<String, String> props = new HashMap<String, String>();

    public WMSGrabber(MapView mv, WMSLayer layer) {
        super(mv, layer);
        this.baseURL = layer.getInfo().getUrl();
        this.serverProjections = layer.getServerProjections();
        /* URL containing placeholders? */
        urlWithPatterns = ImageryInfo.isUrlWithPatterns(baseURL);
        if(layer.getInfo().getCookies() != null && !layer.getInfo().getCookies().equals("")) {
            props.put("Cookie", layer.getInfo().getCookies());
        }
        props.put("User-Agent", Main.pref.get("imagery.wms.user_agent", Version.getInstance().getAgentString()));
        Pattern pattern = Pattern.compile("\\{header\\(([^,]+),([^}]+)\\)\\}");
        StringBuffer output = new StringBuffer();
        Matcher matcher = pattern.matcher(this.baseURL);
        while (matcher.find()) {
            props.put(matcher.group(1),matcher.group(2));
            matcher.appendReplacement(output, "");
        }
        matcher.appendTail(output);
        this.baseURL = output.toString();
    }

    @Override
    void fetch(WMSRequest request, int attempt) throws Exception{
        URL url = null;
        try {
            url = getURL(
                    b.minEast, b.minNorth,
                    b.maxEast, b.maxNorth,
                    width(), height());
            request.finish(State.IMAGE, grab(url, attempt));

        } catch(Exception e) {
            e.printStackTrace();
            throw new Exception(e.getMessage() + "\nImage couldn't be fetched: " + (url != null ? url.toString() : ""));
        }
    }

    public static final NumberFormat latLonFormat = new DecimalFormat("###0.0000000",
            new DecimalFormatSymbols(Locale.US));

    protected URL getURL(double w, double s,double e,double n,
            int wi, int ht) throws MalformedURLException {
        String myProj = Main.getProjection().toCode();
        String srs = "";
        boolean useepsg = false;
        try
        {
            Matcher m = Pattern.compile(".*SRS=([a-z0-9:]+).*", Pattern.CASE_INSENSITIVE).matcher(baseURL.toUpperCase());
            if(m.matches())
            {
                if(m.group(1).equals("EPSG:4326") && Main.getProjection() instanceof Mercator)
                    useepsg = true;
            } else if(Main.getProjection() instanceof Mercator) {
                useepsg = true;
                srs ="&srs=EPSG:4326";
            } else {
                srs ="&srs="+myProj;
            }
        }
        catch(Exception ex)
        {
        }

        if(useepsg) // don't use mercator code directly
        {
            LatLon sw = Main.getProjection().eastNorth2latlon(new EastNorth(w, s));
            LatLon ne = Main.getProjection().eastNorth2latlon(new EastNorth(e, n));
            myProj = "EPSG:4326";
            s = sw.lat();
            w = sw.lon();
            n = ne.lat();
            e = ne.lon();
        }

        String str = baseURL;
        String bbox = latLonFormat.format(w) + ","
        + latLonFormat.format(s) + ","
        + latLonFormat.format(e) + ","
        + latLonFormat.format(n);

        if (urlWithPatterns) {
            str = str.replaceAll("\\{proj(\\([^})]+\\))?\\}", myProj)
            .replaceAll("\\{bbox\\}", bbox)
            .replaceAll("\\{w\\}", latLonFormat.format(w))
            .replaceAll("\\{s\\}", latLonFormat.format(s))
            .replaceAll("\\{e\\}", latLonFormat.format(e))
            .replaceAll("\\{n\\}", latLonFormat.format(n))
            .replaceAll("\\{width\\}", String.valueOf(wi))
            .replaceAll("\\{height\\}", String.valueOf(ht));
        } else {
            str += "bbox=" + bbox
                    + srs
                    + "&width=" + wi + "&height=" + ht;
            if (!(baseURL.endsWith("&") || baseURL.endsWith("?"))) {
                System.out.println(tr("Warning: The base URL ''{0}'' for a WMS service doesn''t have a trailing ''&'' or a trailing ''?''.", baseURL));
                System.out.println(tr("Warning: Fetching WMS tiles is likely to fail. Please check you preference settings."));
                System.out.println(tr("Warning: The complete URL is ''{0}''.", str));
            }
        }
        return new URL(str.replace(" ", "%20"));
    }

    static public ArrayList<String> getServerProjections(String baseURL, Boolean warn)
    {
        ArrayList<String> serverProjections = new ArrayList<String>();
        try
        {
            Matcher m = Pattern.compile(".*\\{PROJ\\(([^)}]+)\\)\\}.*").matcher(baseURL.toUpperCase());
            if(m.matches())
            {
                boolean hasepsg = false;
                for(String p : m.group(1).split(","))
                {
                    serverProjections.add(p);
                    if(p.equals("EPSG:4326"))
                        hasepsg = true;
                }
                if(hasepsg && !serverProjections.contains(new Mercator().toCode()))
                    serverProjections.add(new Mercator().toCode());
            }
            else
            {
                m = Pattern.compile(".*SRS=([a-z0-9:]+).*", Pattern.CASE_INSENSITIVE).matcher(baseURL.toUpperCase());
                if(m.matches())
                {
                    serverProjections.add(m.group(1));
                    if(m.group(1).equals("EPSG:4326"))
                        serverProjections.add(new Mercator().toCode());
                }
                /* TODO: here should be an "else" code checking server capabilities */
            }
        }
        catch(Exception e)
        {
        }
        if(serverProjections.isEmpty())
            return null;
        if(warn)
        {
            String myProj = Main.getProjection().toCode().toUpperCase();
            if(!serverProjections.contains(myProj))
            {
                JOptionPane.showMessageDialog(Main.parent,
                        tr("The projection ''{0}'' in URL and current projection ''{1}'' mismatch.\n"
                                + "This may lead to wrong coordinates.",
                                serverProjections.get(0), myProj),
                                tr("Warning"),
                                JOptionPane.WARNING_MESSAGE);
            }
        }
        return serverProjections;
    }

    @Override
    public boolean loadFromCache(WMSRequest request) {
        BufferedImage cached = layer.cache.getExactMatch(Main.getProjection(), pixelPerDegree, b.minEast, b.minNorth);

        if (cached != null) {
            request.finish(State.IMAGE, cached);
            return true;
        } else if (request.isAllowPartialCacheMatch()) {
            BufferedImage partialMatch = layer.cache.getPartialMatch(Main.getProjection(), pixelPerDegree, b.minEast, b.minNorth);
            if (partialMatch != null) {
                request.finish(State.PARTLY_IN_CACHE, partialMatch);
                return true;
            }
        }

        if((!request.isReal() && !layer.hasAutoDownload())){
            request.finish(State.NOT_IN_CACHE, null);
            return true;
        }

        return false;
    }

    protected BufferedImage grab(URL url, int attempt) throws IOException, OsmTransferException {
        System.out.println("Grabbing WMS " + (attempt > 1? "(attempt " + attempt + ") ":"") + url);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        for(Entry<String, String> e : props.entrySet()) {
            conn.setRequestProperty(e.getKey(), e.getValue());
        }
        conn.setConnectTimeout(Main.pref.getInteger("socket.timeout.connect",15) * 1000);
        conn.setReadTimeout(Main.pref.getInteger("socket.timeout.read", 30) * 1000);

        String contentType = conn.getHeaderField("Content-Type");
        if( conn.getResponseCode() != 200
                || contentType != null && !contentType.startsWith("image") )
            throw new IOException(readException(conn));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = new ProgressInputStream(conn, null);
        try {
            Utils.copyStream(is, baos);
        } finally {
            is.close();
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BufferedImage img = layer.normalizeImage(ImageIO.read(bais));
        bais.reset();
        layer.cache.saveToCache(layer.isOverlapEnabled()?img:null, bais, Main.getProjection(), pixelPerDegree, b.minEast, b.minNorth);
        return img;
    }

    protected String readException(URLConnection conn) throws IOException {
        StringBuilder exception = new StringBuilder();
        InputStream in = conn.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        try {
            String line = null;
            while( (line = br.readLine()) != null) {
                // filter non-ASCII characters and control characters
                exception.append(line.replaceAll("[^\\p{Print}]", ""));
                exception.append('\n');
            }
            return exception.toString();
        } finally {
            br.close();
        }
    }
}
