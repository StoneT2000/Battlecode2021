# Battlecode 2021 Scaffold

This is the Battlecode 2021 scaffold, containing an `examplefuncsplayer`. Read https://2021.battlecode.org/getting-started!

### Project Structure

- `README.md`
    This file.
- `build.gradle`
    The Gradle build file used to build and run players.
- `src/`
    Player source code.
- `test/`
    Player test code.
- `client/`
    Contains the client. The proper executable can be found in this folder (don't move this!)
- `build/`
    Contains compiled player code and other artifacts of the build process. Can be safely ignored.
- `matches/`
    The output folder for match files.
- `maps/`
    The default folder for custom maps.
- `gradlew`, `gradlew.bat`
    The Unix (OS X/Linux) and Windows versions, respectively, of the Gradle wrapper. These are nifty scripts that you can execute in a terminal to run the Gradle build tasks of this project. If you aren't planning to do command line development, these can be safely ignored.
- `gradle/`
    Contains files used by the Gradle wrapper scripts. Can be safely ignored.


### Useful Commands

- `./gradlew run`
    Runs a game with the settings in gradle.properties
- `./gradlew update`
    Update to the newest version! Run every so often

## Random strats we thought of

- Mega brain / computer of units in the back sending info to the front
- Using slowness of slanderers to determine if unit is politician or slanderer
- Muckrakers build with 1 influence, makes politicians waste influence on them. Sending them one by one can cause a lot of trouble
- Muckraker wall to protect slanderers in the back cheaply.
- have Beefy politicians that defend
- Slanderers in multiples of 20. Politicians also maybe to hide them. 

## Important questions to answer ASAP
Q: How many dudes can a center build per round? Is it limited by space around the center...  
A:

Q: Can a bot tell they are near the edge of the map?  
A:

Q: Bytecode is less than last year? What pathing to use.  
A: 

Q: Can we acess the value of the current empower buff value globally?   
A:

Q: What information do we know about the bids once they occur? Can we get the winning bid? Can we see the other guy's bid?  
A:
