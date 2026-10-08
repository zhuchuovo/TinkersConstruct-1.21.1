package slimeknights.tconstruct.tools;

import net.minecraft.ResourceLocationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the material data that loot and mob generated tools carry.
 * <p>
 * {@code MobEquipment#apply} and {@code AddToolDataFunction#run} build tools from a bare item plus
 * {@code RandomMaterial.ancient()}, the only material source that may pick the hidden {@code tconstruct:ancient}
 * material. A tool whose custom data holds a string above 65535 encoded bytes cannot be written into any packet, so
 * these tests pin the two properties that keep that data bounded: variants cannot carry wide characters, and reading
 * then writing the material list reproduces the same tag instead of lengthening it.
 */
class MobLootToolEncodingTest extends BaseMcTest {
  /** Network NBT writes strings with a 16 bit length prefix, so an encoded string may never exceed 65535 bytes. */
  private static final int MAX_NETWORK_STRING_BYTES = 65535;
  private static final String MATERIALS_TAG = "tic_materials";
  private static final MaterialId ANCIENT = new MaterialId("tconstruct", "ancient");

  @Test
  void materialVariantsCannotCarryWideCharacters() {
    // the material list reaches the client as MaterialVariantId#toString, and a variant is restricted to a resource
    // location path. That closes the three bytes per character inflation which would turn the 16 bit length prefix
    // of a network NBT string into a UTFDataFormatException.
    assertThatThrownBy(() -> {
      MaterialVariantId.create(ANCIENT, "远古");
    }).isInstanceOf(ResourceLocationException.class);
    assertThat(MaterialVariantId.create(ANCIENT, "head").toString()).isEqualTo("tconstruct:ancient#head");
    assertThat(MaterialId.tryParse("tconstruct:ancient#头")).isNull();
  }

  @Test
  void materialListRoundTripDoesNotGrow() {
    MaterialNBT materials = MaterialNBT.builder()
      .add(MaterialVariantId.create(ANCIENT, "head"))
      .add(new MaterialId("tconstruct", "manyullyn"))
      .build();
    CompoundTag tag = new CompoundTag();
    tag.put(MATERIALS_TAG, materials.serializeToNBT());
    Tag original = tag.get(MATERIALS_TAG);
    assertThat(original).isNotNull();

    // reading and writing the list again must produce the exact same tag, so repeated loads cannot lengthen it
    Tag reread = MaterialNBT.readFromNBT(original).serializeToNBT();
    assertThat(reread).isEqualTo(original);
    assertThat(longestStringBytes(reread)).isLessThanOrEqualTo(MAX_NETWORK_STRING_BYTES);
  }

  @Test
  void materialIdListRoundTripDoesNotGrow() {
    MaterialIdNBT materials = new MaterialIdNBT(List.of(MaterialVariantId.create(ANCIENT, "head"), new MaterialId("tconstruct", "manyullyn")));
    CompoundTag tag = new CompoundTag();
    tag.put(MATERIALS_TAG, materials.serializeToNBT());
    Tag original = tag.get(MATERIALS_TAG);
    assertThat(original).isNotNull();

    Tag reread = MaterialIdNBT.readFromNBT(original).serializeToNBT();
    assertThat(reread).isEqualTo(original);
    assertThat(longestStringBytes(reread)).isLessThanOrEqualTo(MAX_NETWORK_STRING_BYTES);
  }

  @Test
  void encodedLengthMatchesWriteUtf() {
    assertThat(encodedLength("tconstruct:ancient")).isEqualTo(18);
    assertThat(encodedLength("远古")).isEqualTo(6);
    assertThat(encodedLength("a\u0000b")).isEqualTo(4);
  }

  /** Encoded length of the longest string in the tag, the value the network length prefix has to hold. */
  private static int longestStringBytes(Tag tag) {
    if (tag instanceof StringTag string) {
      return encodedLength(string.getAsString());
    }
    if (tag instanceof ListTag list) {
      int longest = 0;
      for (Tag element : list) {
        longest = Math.max(longest, longestStringBytes(element));
      }
      return longest;
    }
    if (tag instanceof CompoundTag compound) {
      int longest = 0;
      for (String key : compound.getAllKeys()) {
        longest = Math.max(longest, encodedLength(key));
        Tag value = compound.get(key);
        if (value != null) {
          longest = Math.max(longest, longestStringBytes(value));
        }
      }
      return longest;
    }
    return 0;
  }

  /** Byte length of the modified UTF-8 encoding {@code DataOutputStream#writeUTF} uses. */
  private static int encodedLength(String value) {
    int length = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c >= 0x0001 && c <= 0x007F) {
        length++;
      } else if (c <= 0x07FF) {
        length += 2;
      } else {
        length += 3;
      }
    }
    return length;
  }
}
