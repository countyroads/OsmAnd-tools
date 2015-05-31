package net.osmand.regions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import net.osmand.PlatformUtil;
import net.osmand.data.preparation.IndexCreator;
import net.osmand.data.preparation.MapZooms;
import net.osmand.impl.ConsoleProgressImplementation;
import net.osmand.osm.MapRenderingTypesEncoder;
import net.osmand.osm.util.WikipediaByCountryDivider;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class CountryOcbfGeneration {
	private int OSM_ID=-1000;
	private static final Log log = PlatformUtil.getLog(CountryOcbfGeneration.class);

	public static void main(String[] args) throws XmlPullParserException, IOException, SAXException, SQLException, InterruptedException {
		String repo =  "/Users/victorshcherb/osmand/repos/";
		if(args != null && args.length > 0) {
			repo = args[0];
		}
		String regionsXml = repo+"resources/countries-info/regions.xml";
//		String targetObf = repo+"resources/countries-info/countries.reginfo";
		String targetObf = repo+"regions.ocbf";
		
		String targetOsmXml = repo+"resources/countries-info/countries.osm";
		String[] polygonFolders = new String[] {
				repo +"misc/osm-planet/polygons",
//				repo +"misc/osm-planet/gislab-polygons",
//				repo +"misc/osm-planet/geo-polygons",	
				repo +"misc/osm-planet/srtm-polygons"
		};
		String[] translations = new String[] {
				repo +"misc/osm-planet/osm-data/states_places.osm",
				repo +"misc/osm-planet/osm-data/states_regions.osm",
				repo +"misc/osm-planet/osm-data/countries_places.osm",
				repo +"misc/osm-planet/osm-data/countries_admin_level_2.osm"
		};
		new CountryOcbfGeneration().generate(regionsXml, polygonFolders,
				translations, targetObf, targetOsmXml);
	}
	
	private static class TranslateEntity {
		private Map<String, String> tm = new TreeMap<String, String>();

		public boolean isEmpty() {
			return tm.isEmpty();
		}
	}
	
	private static class CountryRegion {
		CountryRegion parent = null;
		List<CountryRegion> children = new ArrayList<CountryRegion>();
		String name;
		String downloadSuffix;
		String innerDownloadSuffix;
		String downloadPrefix;
		String innerDownloadPrefix;
		
		String boundary;
		String translate;
		
		
		public boolean map ;
		public boolean wiki;
		public boolean roads ;
		public boolean hillshade ;
		public boolean srtm ;
		
		public String getFullName() {
			if(parent == null) {
				return name;
			} else {
				return parent.getFullName() + "_" + name;
			}
		}
		
		public String getDownloadName() {
			String s = name;
			String p = getDownloadPrefix();
			if (p != null && p.length() > 0) {
				s = p + "_" + s;
			}
			String suf = getDownloadSuffix();
			if (s != null && s.length() > 0) {
				s = s + "_" + suf;
			}
			return s;
		}
		
		
		public String getInnerDownloadPrefix() {
			if(innerDownloadPrefix != null) {
				return innerDownloadPrefix;
			}
			return getDownloadPrefix();
		}
		
		public String getDownloadPrefix() {
			if(downloadPrefix == null && parent != null) {
				return parent.getInnerDownloadPrefix();
			}
			return downloadPrefix == null ? "" : downloadPrefix;
		}
		
		public String getInnerDownloadSuffix() {
			if(innerDownloadSuffix != null) {
				return innerDownloadSuffix;
			}
			return getDownloadSuffix();
		}
		
		public String getDownloadSuffix() {
			if(downloadSuffix == null && parent != null) {
				return parent.getInnerDownloadSuffix();
			}
			return downloadSuffix == null ? "" : downloadSuffix;
		}

		public void setInnerDownloadSuffix(String string) {
			if(string != null) {
				if("$name".equals(string)) {
					innerDownloadSuffix = name;
				} else {
					innerDownloadSuffix = string;
				}
			}
		}

		public void setDownloadPrefix(String string) {
			if(string != null) {
				if("$name".equals(string)) {
					downloadPrefix = name;
				} else {
					downloadPrefix = string;
				}
			}
		}

		public void setDownloadSuffix(String string) {
			if(string != null) {
				if("$name".equals(string)) {
					downloadSuffix = name;
				} else {
					downloadSuffix = string;
				}
			}
		}

		public void setInnerDownloadPrefix(String string) {
			if(string != null) {
				if("$name".equals(string)) {
					innerDownloadPrefix = name;
				} else {
					innerDownloadPrefix = string;
				}
			}
		}
	}
	
	private void scanTranslates(File file, Map<String, Set<TranslateEntity>> translates) throws XmlPullParserException, IOException {
		XmlPullParser parser = PlatformUtil.newXMLPullParser();
		parser.setInput(new FileReader(file));
		int tok;
		TranslateEntity te = null;
		while ((tok = parser.next()) != XmlPullParser.END_DOCUMENT) {
			if (tok == XmlPullParser.START_TAG) {
				String name = parser.getName();
				if (name.equals("way") || name.equals("node") || name.equals("relation")) {
					te = new TranslateEntity();
				} else if(name.equals("tag") && te != null) {
					Map<String, String> attrs = new LinkedHashMap<String, String>();
					for (int i = 0; i < parser.getAttributeCount(); i++) {
						attrs.put(parser.getAttributeName(i), parser.getAttributeValue(i));
					}
					te.tm.put(attrs.get("k"), attrs.get("v"));
				}
			} else if (tok == XmlPullParser.END_TAG) {
				String name = parser.getName();
				if (name.equals("way") || name.equals("node") || name.equals("relation")) {
					if(!te.isEmpty()) {
						Iterator<Entry<String, String>> it = te.tm.entrySet().iterator();
						while(it.hasNext()) {
							Entry<String, String> e = it.next();
							addTranslate(translates, te, e.getKey().toLowerCase() +"="+e.getValue().toLowerCase());
						}
					}
					te = null;
				}
			}
		}
	}

	private void addTranslate(Map<String, Set<TranslateEntity>> translates, TranslateEntity te, String k) {
		if(!translates.containsKey(k)) {
			translates.put(k, new HashSet<CountryOcbfGeneration.TranslateEntity>());
		}
		translates.get(k).add(te);
	}

	private void generate(String regionsXml, String[] polygonFolders, 
			String[] translations, String targetObf, String targetOsmXml) throws XmlPullParserException, IOException, SAXException, SQLException, InterruptedException {
		Map<String, File> polygonFiles = new LinkedHashMap<String, File>();
		for (String folder : polygonFolders) {
			scanPolygons(new File(folder), polygonFiles);
		}
		Map<String, Set<TranslateEntity>> translates = new TreeMap<String, Set<TranslateEntity>>();
		for (String t : translations) {
			scanTranslates(new File(t), translates);
		}
		XmlPullParser parser = PlatformUtil.newXMLPullParser();
		parser.setInput(new FileReader(regionsXml));
		int tok;
		CountryRegion global = new CountryRegion();
		List<CountryRegion> stack = new ArrayList<CountryOcbfGeneration.CountryRegion>();
		stack.add(global);
		CountryRegion current = global;
		while ((tok = parser.next()) != XmlPullParser.END_DOCUMENT) {
			if (tok == XmlPullParser.START_TAG) {
				String name = parser.getName();
				if (name.equals("region")) {
					Map<String, String> attrs = new LinkedHashMap<String, String>();
					for (int i = 0; i < parser.getAttributeCount(); i++) {
						attrs.put(parser.getAttributeName(i), parser.getAttributeValue(i));
					}
					CountryRegion cr = createRegion(current, attrs);
					stack.add(cr);
					current = cr;
				}
			} else if (tok == XmlPullParser.END_TAG) {
				String name = parser.getName();
				if (name.equals("region")) {
					stack.remove(stack.size() - 1);
					current = stack.get(stack.size() - 1);
				}
			}
		}
		createFile(global, translates, polygonFiles, targetObf, targetOsmXml);

	}

	

	private void createFile(CountryRegion global, Map<String, Set<TranslateEntity>> translates, Map<String, File> polygonFiles,
			String targetObf, String targetOsmXml) throws IOException, SAXException, SQLException, InterruptedException {
		File osm = new File(targetOsmXml);
		XmlSerializer serializer = new org.kxml2.io.KXmlSerializer();
		FileOutputStream fous = new FileOutputStream(osm);
		serializer.setOutput(fous, "UTF-8");
		serializer.startDocument("UTF-8", true);
		serializer.startTag(null, "osm");
		serializer.attribute(null, "version", "0.6");
		serializer.attribute(null, "generator", "OsmAnd");
		serializer.setFeature(
				"http://xmlpull.org/v1/doc/features.html#indent-output", true);

		for(CountryRegion r : global.children) {
			r.parent = null;
			processRegion(r, translates, polygonFiles, targetObf, targetOsmXml, "", serializer);
		}
		
		serializer.endDocument();
		serializer.flush();
		fous.close();

		IndexCreator creator = new IndexCreator(new File(targetObf).getParentFile()); //$NON-NLS-1$
		creator.setMapFileName(new File(targetObf).getName());
		creator.setIndexMap(true);
		creator.setIndexAddress(false);
		creator.setIndexPOI(false);
		creator.setIndexTransport(false);
		creator.setIndexRouting(false);
		MapZooms zooms = MapZooms.parseZooms("5-6");
		creator.generateIndexes(osm,
				new ConsoleProgressImplementation(1), null, zooms, MapRenderingTypesEncoder.getDefault(), log);

		
	}
	
	private static void addTag(XmlSerializer serializer, String key, String value) throws IOException {
		serializer.startTag(null, "tag");
		serializer.attribute(null, "k", key);
		serializer.attribute(null, "v", value);
		serializer.endTag(null, "tag");
	}

	private void processRegion(CountryRegion r, Map<String, Set<TranslateEntity>> translates,
			Map<String, File> polygonFiles, String targetObf, String targetOsmXml, String indent, XmlSerializer serializer) 
					throws IOException {
		String line = "key= " + r.name;
		File boundary = null;
		if (r.boundary != null) {
			if (!polygonFiles.containsKey(r.boundary)) {
				System.out.println("!!! Missing boundary " + r.boundary);
			} else {
				boundary = polygonFiles.get(r.boundary);
				line += " boundary="+boundary.getName();
			}
		}
		if(boundary != null) {
			List<List<String>> boundaryPoints = readBoundaryPoints(boundary, serializer);
			serializer.startTag(null, "way");
			serializer.attribute(null, "id", OSM_ID-- +"");
			serializer.attribute(null, "visible", "true");
			// TODO relation
			for (List<String> ls : boundaryPoints) {
				for (String bnd : ls) {
					serializer.startTag(null, "nd");
					serializer.attribute(null, "ref", bnd);
					serializer.endTag(null, "nd");
				}
			}
		} else {
			serializer.startTag(null, "node");
			serializer.attribute(null, "id", OSM_ID-- +"");
			serializer.attribute(null, "visible", "true");
			serializer.attribute(null, "lat", "0");
			serializer.attribute(null, "lon", "0");
		}
		
		addTag(serializer, "key_name", r.name);
		addTag(serializer, "region_full_name", r.getFullName());
		if(r.parent != null) {
			addTag(serializer, "region_parent_name", r.parent.getFullName());
		}
		if(r.map || r.roads || r.wiki || r.srtm || r.hillshade) {
			line += " download=" + r.getDownloadName();
			addTag(serializer, "download_name", r.getDownloadName());
			addTag(serializer, "region_prefix", r.getDownloadPrefix());
			addTag(serializer, "region_suffix", r.getDownloadSuffix()); // add exception for Russia for BW?
			if(r.map) {
				line += " map=yes";
				addTag(serializer, "region_map", "yes");
			}
			if(r.wiki) {
				line += " wiki=yes";
				addTag(serializer, "region_wiki", "yes");
			}
			if(r.roads) {
				line += " roads=yes";
				addTag(serializer, "region_roads", "yes");
			}
			if(r.srtm) {
				line += " srtm=yes";
				addTag(serializer, "region_srtm", "yes");
			}
			if(r.hillshade) {
				line += " hillshade=yes";
				addTag(serializer, "region_hillshade", "yes");
			}
		}
		if(r.translate == null) {
			line += " translate-no=" + Algorithms.capitalizeFirstLetterAndLowercase(r.name);
		} else if(r.translate.startsWith("=")) {
			line += " translate-assign=" + r.translate.substring(1);
		} else {
			String[] tags = r.translate.split(";");
			Set<TranslateEntity> set = null;
			for(String t : tags) {
				if(!t.contains("=")) {
					if(translates.containsKey("name="+t)) {
						t = "name=" +t;	
					} else if(translates.containsKey("name:en="+t)) {
						t = "name:en=" + t;
					}
				}
				if(set == null) {
					set = translates.get(t);
				} else {
					Set<TranslateEntity> st2 = translates.get(t);
					if(st2 != null) {
						set = new HashSet<TranslateEntity>(set);
						set.retainAll(st2);
					}
				}
			}
			if(set == null) {
				System.out.println("!!! Couldn't find translation name " + r.translate);
			} else if(set.size() > 1) {
				System.out.println("!!! More than 1 translation " + r.translate);
			} else {
				TranslateEntity nt = set.iterator().next();
				line += " translate-" + nt.tm.size() + "=" + nt.tm.get("name");
				Iterator<Entry<String, String>> it = nt.tm.entrySet().iterator();
				while(it.hasNext()) {
					Entry<String, String> e = it.next();
					addTag(serializer, e.getKey(), e.getValue());
				}
			}
		}
		
		// COMMENT TO SEE ONLY WARNINGS
//		System.out.println(indent + line);
		
		
		if(boundary != null) {
			serializer.endTag(null, "way");
		} else {
			serializer.endTag(null, "node");
		}
		
		
		for(CountryRegion c : r.children) {
			processRegion(c, translates, polygonFiles, targetObf, targetOsmXml, indent + "  ", serializer);
		}		
	}

	private List<List<String>> readBoundaryPoints(File boundary, XmlSerializer serializer) throws IOException {
		List<List<String>> res = new ArrayList<List<String>>();
		List<String> l = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(boundary));
		br.readLine(); // name
		boolean newContour = true;
		String s;
		while ((s = br.readLine()) != null) {
			if (newContour) {
				// skip
				newContour = false;
				if(l.size()  >0) {
					res.add(l);
				}
				l = new ArrayList<String>();
			} else if (s.trim().length() == 0) {
			} else if (s.trim().equals("END")) {
				newContour = true;
			} else {
				s = s.trim();
				int i = s.indexOf(' ');
				if(i == -1) {
					i = s.indexOf('\t');
				}
				if(i == -1) {
					System.err.println("? " +s);
				}
				String lat = s.substring(i, s.length()).trim();
				String lon = s.substring(0, i).trim();
				serializer.startTag(null, "node");
				try {
					serializer.attribute(null, "lat", Double.parseDouble(lat)+"");
					serializer.attribute(null, "lon", Double.parseDouble(lon)+"");
				} catch (NumberFormatException e) {
					System.err.println(lat + " " + lon);
					e.printStackTrace();
				}
				long id = OSM_ID--;
				l.add(id + "");
				serializer.attribute(null, "id", id + "");
				serializer.endTag(null, "node");
			}
		}
		if(l.size()  >0) {
			res.add(l);
		}
		br.close();
		return res;
	}

	private CountryRegion createRegion(CountryRegion parent, Map<String, String> attrs) {
		CountryRegion reg = new CountryRegion();
		reg.parent = parent;
		if(parent != null) {
			parent.children.add(reg);
		}
		String type = attrs.get("type");
		reg.name = attrs.get("name");
		reg.setDownloadSuffix(attrs.get("download_suffix"));
		reg.setDownloadPrefix(attrs.get("download_prefix"));
		reg.setInnerDownloadSuffix(attrs.get("inner_download_suffix"));
		reg.setInnerDownloadPrefix(attrs.get("inner_download_prefix"));
		if(attrs.containsKey("hillshade")) {
			reg.hillshade = Boolean.parseBoolean(attrs.get("hillshade"));
		} else {
			reg.hillshade = type == null || type.equals("hillshade"); 
		}
		if(attrs.containsKey("srtm")) {
			reg.srtm = Boolean.parseBoolean(attrs.get("srtm"));
		} else {
			reg.srtm = type == null || type.equals("srtm"); 
		}
		if(attrs.containsKey("map")) {
			reg.map = Boolean.parseBoolean(attrs.get("map"));
		} else {
			reg.map = type == null || type.equals("map"); 
		}
		if(attrs.containsKey("roads")) {
			reg.roads = Boolean.parseBoolean(attrs.get("roads"));
		} else {
			reg.roads = reg.map;
		}
		if(attrs.containsKey("wiki")) {
			reg.wiki = Boolean.parseBoolean(attrs.get("wiki"));
		} else {
			reg.wiki = reg.map;
		}
		if(attrs.containsKey("translate")) {
			reg.translate = attrs.get("translate");
			if(reg.translate.equals("no")) {
				reg.translate = null;
			}
		} else {
			reg.translate = reg.name;
		}
		if(attrs.containsKey("boundary")) {
			reg.boundary = attrs.get("boundary");
			if(reg.boundary.equals("no")) {
				reg.boundary = null;
			}
		} else {
			reg.boundary = reg.name;
		}
		return reg;
	}

	private void scanPolygons(File file, Map<String, File> polygonFiles) {
		if(file.isDirectory()) {
			for(File c : file.listFiles()) {
				if(c.isDirectory()) {
					scanPolygons(c, polygonFiles);
				} else if(c.getName().endsWith(".poly")) {
					String name = c.getName().substring(0, c.getName().length() - 5);
					if(!polygonFiles.containsKey(name)) {
						polygonFiles.put(name, c);
					} else {
						File rm = polygonFiles.get(name);
						System.out.println("Polygon duplicate -> " + rm.getParentFile().getName() + "/" + name + " and " + 
								c.getParentFile().getName() + "/" + name);
						polygonFiles.put(rm.getParentFile().getName() + "/" + name, rm);
						polygonFiles.put(c.getParentFile().getName() + "/" + name, c);
					}
				}
			}
		}
		
	}
}