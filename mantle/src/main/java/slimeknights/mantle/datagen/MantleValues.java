package slimeknights.mantle.datagen;

/** Contains some constants used for values shared across SlimeKnights mods */
public interface MantleValues {
  /** Amount of mb of a bowl, such as mushroom stew or beetroot soup */
  int BOWL = 250;
  /** Amount of mb of a bottle, such as a potion or honey bottle */
  int BOTTLE = 250;
  /** Division of water */
  int DROP = BOTTLE / 5;
  /** Division of an edible bowl or bottle */
  int SIP = BOWL / 5;
}
