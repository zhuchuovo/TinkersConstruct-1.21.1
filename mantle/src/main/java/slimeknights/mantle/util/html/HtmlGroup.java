package slimeknights.mantle.util.html;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Represents a group containing 1 or more nested child elements. Used for the final page layout and for text spans within a complex element type. */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class HtmlGroup implements HtmlSerializable {
  protected final boolean indentChildren;
  protected final List<HtmlSerializable> children = new ArrayList<>();

  /** Creates a group with indentation */
  public static HtmlGroup indent() {
    return new HtmlGroup(true);
  }

  /** Creates a group inline */
  public static HtmlGroup inline() {
    return new HtmlGroup(false);
  }


  /** Adds a nested element */
  public HtmlGroup add(HtmlSerializable element) {
    children.add(element);
    return this;
  }

  /** Adds a list of nested elements */
  public HtmlGroup add(HtmlSerializable... elements) {
    for (HtmlSerializable element : elements) {
      add(element);
    }
    return this;
  }

  /** Adds a list of nested elements */
  public HtmlGroup add(Stream<? extends HtmlSerializable> elements) {
    elements.forEachOrdered(this::add);
    return this;
  }

  /** Nests a string */
  public HtmlGroup add(String text) {
    return add(new HtmlString(text));
  }

  /** Converts this group to an HTML string */
  public String toHtml() {
    StringBuilder builder = new StringBuilder();
    toHtml(builder, "");
    return builder.toString();
  }

  @Override
  public void toHtml(StringBuilder builder, String indent) {
    if (indentChildren) {
      int max = children.size() - 1;
      for (int i = 0; i <= max; i++) {
        // nested groups may apply the starting indent multiple times
        if (i != 0) {
          builder.append(indent);
        }
        children.get(i).toHtml(builder, indent);
        // nested groups may apply the newline multiple times
        if (i != max) {
          builder.append('\n');
        }
      }
    } else {
      for (HtmlSerializable element : children) {
        element.toHtml(builder, indent);
      }
    }
  }

  @Override
  public String toString() {
    return "Group[" + children.stream().map(Object::toString).collect(Collectors.joining(",")) + ']';
  }
}
