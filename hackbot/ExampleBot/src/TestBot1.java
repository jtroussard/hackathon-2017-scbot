import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;
import java.util.Random;

public class TestBot1 extends DefaultBWListener {

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;
    
    public int workers;
    public int marines;
    
    Random rand = new Random();

    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        if (unit.getType() == UnitType.Terran_SCV) {
        	workers++;
        } else if (unit.getType() == UnitType.Terran_Marine) {
        	marines++;
        }
        System.out.println("New unit discovered " + unit.getType() + "\n\tWORKERS = " + workers + "\n\tMARINES = " + marines);

    }

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }
        
        for (Unit u : self.getUnits()) {
        	if (u.getType() == UnitType.Terran_SCV) {
        		workers++;
        	}
        }

    }

    @Override
    public void onFrame() {
        //game.setTextSize(10);
        game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());

        StringBuilder units = new StringBuilder("My units:\n");

        //iterate through my units
        for (Unit myUnit : self.getUnits()) {
            units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");
            game.drawTextMap(myUnit.getPosition().getX(), myUnit.getPosition().getY(), myUnit.getOrder().toString());
            game.drawLineMap(myUnit.getPosition().getX(), myUnit.getPosition().getY(), myUnit.getOrderTargetPosition().getX(),
            		myUnit.getOrderTargetPosition().getY(), bwapi.Color.Black);

            // If there are enough minerals, build an SCV
            // - Do not queue more than two SCVs
            // - Standard limit of 30 SCVs
            if (myUnit.getType() == UnitType.Terran_Command_Center && self.minerals() >= 50 && self.allUnitCount(UnitType.Terran_SCV) < 30) {
            	if (myUnit.getTrainingQueue().size() < 2) {
            		myUnit.train(UnitType.Terran_SCV);
            	}
            }

            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker() && myUnit.isIdle()) {
                Unit closestMineral = null;

                //find the closest mineral
                boolean mineralFound = false;
                for (Unit neutralUnit : game.neutral().getUnits()) {
                    if (neutralUnit.getType().isMineralField()) {
                        if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
                            closestMineral = neutralUnit;
                            mineralFound = true;
                        }
                    }
                }
//                // if there is nothing to do go explore
//                if (!myUnit.isConstructing() || !myUnit.isGatheringMinerals() || !myUnit.isGatheringGas()) {
//                	int randomX = rand.nextInt(game.mapWidth());
//                	int randomY = rand.nextInt(game.mapHeight());
//                	System.out.println("Bored SCV is going to (" + randomX + ", " + randomY + ")");
//                }

                //if a mineral patch was found, send the worker to gather it
                if (closestMineral != null) {
                    myUnit.gather(closestMineral, false);
                }
            }
            
            //if we're running out of supply and have enough minerals ...
            if ((self.supplyTotal() - self.supplyUsed() < 2) && (self.minerals() >= 100)) {
            	//iterate over units to find a worker
        		if (myUnit.getType() == UnitType.Terran_SCV) {
        			//get a nice place to build a supply depot
        			TilePosition buildTile =
        				getBuildTile(myUnit, UnitType.Terran_Supply_Depot, self.getStartLocation());
        			//and, if found, send the worker to build it (and leave others alone - break;)
        			if (buildTile != null) {
        				myUnit.build(UnitType.Terran_Supply_Depot, buildTile);
        				break;
        			}
        		}
            }
            
            // If there are enough minerals and supply, build a barrack.
            // - Limit barracks to 2
            if ((self.supplyTotal() - self.supplyUsed() > 0) && (self.minerals() > 250) && self.allUnitCount(UnitType.Terran_Barracks) < 2) {
            	if (myUnit.getType() == UnitType.Terran_SCV) {
        			//get a nice place to build a barrack
        			TilePosition buildTile =
        				getBuildTile(myUnit, UnitType.Terran_Barracks, self.getStartLocation());
        			//and, if found, send the worker to build it (and leave others alone - break;)
        			if (buildTile != null) {
        				myUnit.build(UnitType.Terran_Barracks, buildTile);
        				break;
        			}
        		}
            }
            
            //if we have enough workers and supply build some marines ...
            if ((self.supplyUsed() < self.supplyTotal()) && (self.minerals() >= 150)) {
            	//iterate over units to find cc
        		if (myUnit.getType() == UnitType.Terran_Barracks) {
        			myUnit.train(UnitType.Terran_Marine);
        			break;
        		}
            }
        }

        //draw my units on screen
        game.drawTextScreen(10, 25, units.toString());
    }
    
    public TilePosition getBuildTile(Unit builder, UnitType building, TilePosition startTile) {
    	TilePosition result = null;
    	int maxDist = 3;
    	int stopDist = 40;

    	// Refinery, Assimilator, Extractor
    	if (building.isRefinery()) {
    		for (Unit n : game.neutral().getUnits()) {
    			if ((n.getType() == UnitType.Resource_Vespene_Geyser) &&
    					( Math.abs(n.getTilePosition().getX() - startTile.getX()) < stopDist ) &&
    					( Math.abs(n.getTilePosition().getY() - startTile.getY()) < stopDist )
    					) {
    				// TODO draw some boxes
    				return n.getTilePosition();
    			}
    		}
    	}
    	
    	// Otherwise look for regular building area
    	while ((maxDist < stopDist) && result == null) {
    		for (int i=startTile.getX()-maxDist; i<=startTile.getX()+maxDist; i++) {
    			for (int j=startTile.getY()-maxDist; j<=startTile.getY()+maxDist; j++) {
    				if (game.canBuildHere(new TilePosition(i,j), building, builder, false)) {
    					// units that are blocking the tile
    					boolean unitsInWay = false;
    					for (Unit u : game.getAllUnits()) {
    						if (u.getID() == builder.getID()) continue;
    						if ((Math.abs(u.getTilePosition().getX()-i) < 4) && (Math.abs(u.getTilePosition().getY()-j) < 4)) unitsInWay = true;
    					}
    					if (!unitsInWay) {
    						return new TilePosition(i, j);
    					}
    				}
    			}
    		}
    		maxDist += 2;
    	}
    	if (result == null) game.printf("Unable to find suitable build position for "+building.toString());
    	return result;
    }

    public static void main(String[] args) {
        new TestBot1().run();
    }
}