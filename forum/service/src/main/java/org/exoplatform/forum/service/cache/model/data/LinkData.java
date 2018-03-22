package org.exoplatform.forum.service.cache.model.data;

import org.exoplatform.forum.common.cache.model.CachedData;
import org.exoplatform.forum.service.ForumLinkData;

import java.util.Objects;

public class LinkData implements CachedData<ForumLinkData> {

  private final String id;
  private final String name;
  private final String path;
  private final String type;
  private final boolean isClosed;
  private final boolean isLock;

  public LinkData(ForumLinkData link) {
    this.id = link.getId();
    this.name = link.getName();
    this.path = link.getPath();
    this.type = link.getType();
    this.isClosed = link.getIsClosed();
    this.isLock = link.getIsLock();
  }

  public ForumLinkData build() {

    ForumLinkData link = new ForumLinkData();
    link.setId(this.id);
    link.setName(this.name);
    link.setPath(this.path);
    link.setType(this.type);
    link.setIsClosed(this.isClosed);
    link.setIsLock(this.isLock);
    return link;
    
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LinkData linkData = (LinkData) o;
    return isClosed == linkData.isClosed &&
            isLock == linkData.isLock &&
            Objects.equals(id, linkData.id) &&
            Objects.equals(name, linkData.name) &&
            Objects.equals(path, linkData.path) &&
            Objects.equals(type, linkData.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, path, type, isClosed, isLock);
  }
}
