package slimeknights.mantle.util.html;

/** Common interface for HTML elements. */
public interface HtmlSerializable {
  /** Empty element containing no HTML */
  HtmlSerializable EMPTY = (builder, indent) -> {};

  /**
   * Writes the contents of this object to HTML in the builder.
   * @param builder  String builder for outputs.
   */
  void toHtml(StringBuilder builder, String indent);
}
