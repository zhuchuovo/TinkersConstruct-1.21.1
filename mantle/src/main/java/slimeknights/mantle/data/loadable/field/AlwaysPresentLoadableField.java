package slimeknights.mantle.data.loadable.field;

/** Common networking logic for loadables that always have a network value */
public interface AlwaysPresentLoadableField<T,P> extends LoadableField<T,P>, AlwaysPresentRecordField<T,P> {}
