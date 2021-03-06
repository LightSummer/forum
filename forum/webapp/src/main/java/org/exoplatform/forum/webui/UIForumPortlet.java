/***************************************************************************
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 ***************************************************************************/
package org.exoplatform.forum.webui;

import java.net.URLDecoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import javax.portlet.PortletMode;
import javax.portlet.PortletPreferences;
import javax.servlet.http.HttpServletRequest;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.forum.ForumUtils;
import org.exoplatform.forum.common.CommonUtils;
import org.exoplatform.forum.common.UserHelper;
import org.exoplatform.forum.common.webui.BuildRendering;
import org.exoplatform.forum.common.webui.UIPopupAction;
import org.exoplatform.forum.common.webui.UIPopupContainer;
import org.exoplatform.forum.common.webui.WebUIUtils;
import org.exoplatform.forum.service.Category;
import org.exoplatform.forum.service.Forum;
import org.exoplatform.forum.service.ForumService;
import org.exoplatform.forum.service.ForumServiceUtils;
import org.exoplatform.forum.service.Post;
import org.exoplatform.forum.service.Topic;
import org.exoplatform.forum.service.UserProfile;
import org.exoplatform.forum.service.Utils;
import org.exoplatform.forum.webui.popup.UIPostForm;
import org.exoplatform.forum.webui.popup.UIPrivateMessageForm;
import org.exoplatform.forum.webui.popup.UISettingEditModeForm;
import org.exoplatform.forum.webui.popup.UIViewPostedByUser;
import org.exoplatform.forum.webui.popup.UIViewTopicCreatedByUser;
import org.exoplatform.forum.webui.popup.UIViewUserProfile;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiApplication;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.application.portlet.PortletRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.core.UIPortletApplication;
import org.exoplatform.webui.core.lifecycle.UIApplicationLifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.ws.frameworks.cometd.ContinuationService;
import org.mortbay.cometd.continuation.EXoContinuationBayeux;

@ComponentConfig(
                 lifecycle = UIApplicationLifecycle.class,
                 template = "app:/templates/forum/webui/UIForumPortlet.gtmpl",
                 events = {
                   @EventConfig(listeners = UIForumPortlet.ViewPublicUserInfoActionListener.class ) ,
                   @EventConfig(listeners = UIForumPortlet.ViewPostedByUserActionListener.class ),
                   @EventConfig(listeners = UIForumPortlet.PrivateMessageActionListener.class ),
                   @EventConfig(listeners = UIForumPortlet.ViewThreadByUserActionListener.class ),
                   @EventConfig(listeners = UIForumPortlet.OpenLinkActionListener.class)
                 }
)
public class UIForumPortlet extends UIPortletApplication {
  
  public static String RULE_EVENT_PARAMS           = "UIForumPortlet.RuleEventParams";

  private ForumService forumService;

  private boolean      isCategoryRendered  = true;

  private boolean      isForumRendered     = false;

  private boolean      isTagRendered       = false;

  private boolean      isSearchRendered    = false;

  private boolean      isShowPoll          = false;

  private boolean      isShowModerators    = false;

  private boolean      isShowRules         = false;

  private boolean      isShowIconsLegend   = false;

  private boolean      isShowStatistics    = false;

  private boolean      isShowQuickReply    = false;

  private UserProfile  userProfile         = null;

  private boolean      enableIPLogging     = false;

  private boolean      prefForumActionBar  = false;

  private boolean      isRenderActionBar   = false;

  private boolean      enableBanIP         = false;

  private boolean      useAjax             = true;

  protected boolean    forumSpDeleted      = false;

  private int          dayForumNewPost     = 0;
  
  private String       categorySpId        = "";

  private String       forumSpId           = null;

  private String       spaceGroupId        = null;

  protected String       spaceDisplayName  = null;

  private List<String> invisibleForums     = new ArrayList<String>();

  private List<String> invisibleCategories = new ArrayList<String>();

  private PortletMode portletMode;
  public UIForumPortlet() throws Exception {
    forumService = (ForumService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ForumService.class);
    addChild(UIBreadcumbs.class, null, null);
    isRenderActionBar = !UserHelper.isAnonim();
    addChild(UIForumActionBar.class, null, null).setRendered(isRenderActionBar);
    addChild(UICategoryContainer.class, null, null).setRendered(isCategoryRendered);
    addChild(UIForumContainer.class, null, null).setRendered(isForumRendered);
    addChild(UITopicsTag.class, null, null).setRendered(isTagRendered);
    addChild(UISearchForm.class, null, null).setRendered(isSearchRendered);
    UIPopupAction popupAction = addChild(UIPopupAction.class, null, "UIForumPopupAction");
    popupAction.getChild(UIPopupWindow.class).setId("UIForumPopupWindow");
    try {
      loadPreferences();
    } catch (Exception e) {
      log.warn("Failed to load portlet preferences", e);
    }
  }

  public void processRender(WebuiApplication app, WebuiRequestContext context) throws Exception {
    PortletRequestContext portletReqContext = (PortletRequestContext) context;
    portletMode = portletReqContext.getApplicationMode();
    if (portletMode == PortletMode.VIEW) {
      isRenderActionBar = !UserHelper.isAnonim();
      if (getChild(UIBreadcumbs.class) == null) {
        if (getChild(UISettingEditModeForm.class) != null)
          removeChild(UISettingEditModeForm.class);
        addChild(UIBreadcumbs.class, null, null);
        addChild(UIForumActionBar.class, null, null).setRendered(isRenderActionBar);
        UICategoryContainer categoryContainer = addChild(UICategoryContainer.class, null, null).setRendered(isCategoryRendered);
        addChild(UIForumContainer.class, null, null).setRendered(isForumRendered);
        addChild(UITopicsTag.class, null, null).setRendered(isTagRendered);
        addChild(UISearchForm.class, null, null).setRendered(isSearchRendered);
        updateIsRendered(ForumUtils.CATEGORIES);
        categoryContainer.updateIsRender(true);
      }
      updateCurrentUserProfile();
    } else if (portletMode == PortletMode.EDIT) {
      if (getChild(UISettingEditModeForm.class) == null) {
        UISettingEditModeForm editModeForm = addChild(UISettingEditModeForm.class, null, null);
        editModeForm.setInitComponent();
        removeAllChildPorletView();
      }
    }
    try {
      renderComponentByURL(context);
    } catch (Exception e) {
      log.error("Can not open component by url, view exception: ", e);
    }
    BuildRendering.startRender(context);
    super.processRender(app, context);
    BuildRendering.endRender(context);
  }

  private void removeAllChildPorletView() {
    if (getChild(UIBreadcumbs.class) != null) {
      removeChild(UIBreadcumbs.class);
      removeChild(UIForumActionBar.class);
      removeChild(UICategoryContainer.class);
      removeChild(UIForumContainer.class);
      removeChild(UITopicsTag.class);
      removeChild(UISearchForm.class);
    }
  }

  public void renderComponentByURL(WebuiRequestContext context) throws Exception {
    forumSpDeleted = false;
    PortalRequestContext portalContext = Util.getPortalRequestContext();
    String isAjax = portalContext.getRequestParameter("ajaxRequest");
    if (isAjax != null && Boolean.parseBoolean(isAjax))
      return;

    String url = ((HttpServletRequest) portalContext.getRequest()).getRequestURL().toString();
    url = URLDecoder.decode(url, "UTF-8");
    String pageNodeSelected = ForumUtils.SLASH + Util.getUIPortal().getSelectedUserNode().getURI();
    String portalName = Util.getUIPortal().getName();
    if(url.contains(portalName + pageNodeSelected)) {
      url = url.substring(url.lastIndexOf(portalName + pageNodeSelected)+ (portalName + pageNodeSelected).length());
    } else if(url.contains(pageNodeSelected + ForumUtils.SLASH)) {
      url = url.substring(url.lastIndexOf(pageNodeSelected + ForumUtils.SLASH)+ pageNodeSelected.length());
    } else if(url.contains(pageNodeSelected)) {
      url = url.substring(url.lastIndexOf(pageNodeSelected)+ pageNodeSelected.length());
    }
    if(!ForumUtils.isEmpty(url)) {
      url = (url.contains(ForumUtils.SLASH+Utils.FORUM_SERVICE)) ? url.substring(url.lastIndexOf(Utils.FORUM_SERVICE)) :
           ((url.contains(ForumUtils.SLASH+Utils.CATEGORY)) ? url.substring(url.lastIndexOf(ForumUtils.SLASH+Utils.CATEGORY)+1) :
           ((url.contains(ForumUtils.SLASH+Utils.TOPIC)) ? url.substring(url.lastIndexOf(ForumUtils.SLASH+Utils.TOPIC)+1) :
           ((url.contains(ForumUtils.SLASH+Utils.FORUM)) ? url.substring(url.lastIndexOf(ForumUtils.SLASH+Utils.FORUM)+1) : url)));
    } else {
      if (portalName.startsWith(SpaceUtils.SPACE_GROUP)) {
        url = getForumIdOfSpace();
      }
    }
    if (!ForumUtils.isEmpty(url) && url.length() > Utils.FORUM.length()) {
      if(url.indexOf("&") > 0) {
        url = url.substring(0, url.indexOf("&"));
      }
      calculateRenderComponent(url, context);
      context.addUIComponentToUpdateByAjax(this);
    }
  }

  public String getForumIdOfSpace() {
    Space space = WebUIUtils.getSpaceByContext();
    if (space != null) {
      spaceGroupId = space.getGroupId();
      spaceDisplayName = space.getDisplayName();
      forumSpId = Utils.FORUM_SPACE_ID_PREFIX + spaceGroupId.substring(spaceGroupId.indexOf(CommonUtils.SLASH, 1) + 1);
      categorySpId = Utils.CATEGORY_SPACE_ID_PREFIX;
    }
    return forumSpId;
  }

  public void updateIsRendered(String selected){
    if (selected.equals(ForumUtils.CATEGORIES)) {
      isCategoryRendered = true;
      isForumRendered = false;
      isTagRendered = false;
      isSearchRendered = false;
    } else if (selected.equals(ForumUtils.FORUM)) {
      isForumRendered = true;
      isCategoryRendered = false;
      isTagRendered = false;
      isSearchRendered = false;
    } else if (selected.equals(ForumUtils.TAG)) {
      isTagRendered = true;
      isForumRendered = false;
      isCategoryRendered = false;
      isSearchRendered = false;
    } else {
      isTagRendered = false;
      isForumRendered = false;
      isCategoryRendered = false;
      isSearchRendered = true;
    }
    if (prefForumActionBar == false && (isCategoryRendered == false || isSearchRendered)) {
      isRenderActionBar = false;
    }
    getChild(UICategoryContainer.class).setRendered(isCategoryRendered);
    getChild(UIForumActionBar.class).setRendered(isRenderActionBar);
    getChild(UIForumContainer.class).setRendered(isForumRendered);
    getChild(UITopicsTag.class).setRendered(isTagRendered);
    getChild(UISearchForm.class).setRendered(isSearchRendered);
  }

  public void renderForumHome() throws Exception{
    updateIsRendered(ForumUtils.CATEGORIES);
    UICategoryContainer categoryContainer = getChild(UICategoryContainer.class);
    categoryContainer.updateIsRender(true);
    categoryContainer.getChild(UICategories.class).setIsRenderChild(false);
    getChild(UIBreadcumbs.class).setUpdataPath(Utils.FORUM_SERVICE);
  }
  
  public void loadPreferences() throws Exception {
    WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
    if (context instanceof PortletRequestContext){
    PortletRequestContext  pContext = (PortletRequestContext) context;
    PortletPreferences portletPref = pContext.getRequest().getPreferences();
    invisibleCategories.clear();
    invisibleForums.clear();
    prefForumActionBar = Boolean.parseBoolean(portletPref.getValue("showForumActionBar", ForumUtils.EMPTY_STR));
    dayForumNewPost = Integer.parseInt(portletPref.getValue("forumNewPost", ForumUtils.EMPTY_STR));
    useAjax = Boolean.parseBoolean(portletPref.getValue("useAjax", ForumUtils.EMPTY_STR));
    enableIPLogging = Boolean.parseBoolean(portletPref.getValue("enableIPLogging", ForumUtils.EMPTY_STR));
    enableBanIP = Boolean.parseBoolean(portletPref.getValue("enableIPFiltering", ForumUtils.EMPTY_STR));
    isShowPoll = Boolean.parseBoolean(portletPref.getValue("isShowPoll", ForumUtils.EMPTY_STR));
    isShowModerators = Boolean.parseBoolean(portletPref.getValue("isShowModerators", ForumUtils.EMPTY_STR));
    isShowRules = Boolean.parseBoolean(portletPref.getValue("isShowRules", ForumUtils.EMPTY_STR));
    isShowQuickReply = Boolean.parseBoolean(portletPref.getValue("isShowQuickReply", ForumUtils.EMPTY_STR));
    isShowStatistics = Boolean.parseBoolean(portletPref.getValue("isShowStatistics", ForumUtils.EMPTY_STR));
    isShowIconsLegend = Boolean.parseBoolean(portletPref.getValue("isShowIconsLegend", ForumUtils.EMPTY_STR));
    invisibleCategories.addAll(getListInValus(portletPref.getValue("invisibleCategories", ForumUtils.EMPTY_STR)));
    invisibleForums.addAll(getListInValus(portletPref.getValue("invisibleForums", ForumUtils.EMPTY_STR)));
    if (invisibleCategories.size() == 1 && invisibleCategories.get(0).equals(" "))
      invisibleCategories.clear();
    }
  }

  private List<String> getListInValus(String value) throws Exception {
    List<String> list = new ArrayList<String>();
    if (!ForumUtils.isEmpty(value)) {
      list.addAll(Arrays.asList(ForumUtils.addStringToString(value, value)));
    }
    return list;
  }

  public List<String> getInvisibleForums() {
    return invisibleForums;
  }

  public List<String> getInvisibleCategories() {
    return invisibleCategories;
  }

  public boolean isEnableIPLogging() {
    return enableIPLogging;
  }

  public boolean isEnableBanIp() {
    return enableBanIP;
  }

  public boolean isShowForumActionBar() {
    return prefForumActionBar;
  }

  public boolean isShowPoll() {
    return isShowPoll;
  }

  public boolean isShowModerators() {
    return isShowModerators;
  }

  public boolean isShowRules() {
    return isShowRules;
  }

  public boolean isShowIconsLegend() {
    return isShowIconsLegend;
  }

  public boolean isShowQuickReply() {
    return isShowQuickReply;
  }

  public boolean isShowStatistics() {
    return isShowStatistics;
  }

  public boolean isUseAjax() {
    return useAjax;
  }

  public int getDayForumNewPost() {
    return dayForumNewPost;
  }

  public String getSpaceGroupId() {
    return spaceGroupId;
  }
  public void cancelAction() throws Exception {
    WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
    UIPopupAction popupAction = getChild(UIPopupAction.class);
    popupAction.deActivate();
    context.addUIComponentToUpdateByAjax(popupAction);
  }
  
  public void updateCurrentUserProfile() {
    UserProfile lastProfile = userProfile;
    try {
      String userId = UserHelper.getCurrentUser();
      if (enableBanIP) {
        userProfile = forumService.getDefaultUserProfile(userId, WebUIUtils.getRemoteIP());
      } else {
        userProfile = forumService.getDefaultUserProfile(userId, null);
      }
      if (!ForumUtils.isEmpty(userId))
        userProfile.setEmail(UserHelper.getUserByUserId(userId).getEmail());
      if (userProfile.getIsBanned())
        userProfile.setUserRole((long) 3);
    } catch (Exception e) {
      userProfile = new UserProfile();
    }
    if (lastProfile != null) {
      userProfile.getLastAccessTopics().putAll(lastProfile.getLastAccessTopics());
      userProfile.getLastAccessForums().putAll(lastProfile.getLastAccessForums());
      lastProfile = null;
    }
    if(UserProfile.USER_DELETED == userProfile.getUserRole() ||
       UserProfile.GUEST == userProfile.getUserRole() ) {
      isRenderActionBar = false;
    }
    UIForumActionBar actionBar = getChild(UIForumActionBar.class);
    if (actionBar != null) {
      actionBar.setRendered(isRenderActionBar);
    }
  }

  public UserProfile getUserProfile() {
    if (userProfile == null) {
      updateCurrentUserProfile();
    }
    return userProfile;
  }

  public void updateAccessTopic(String topicId) throws Exception {
    String userId = getUserProfile().getUserId();
    if (userId != null && userId.length() > 0) {
      forumService.updateTopicAccess(userId, topicId);
    }
    userProfile.setLastTimeAccessTopic(topicId, CommonUtils.getGreenwichMeanTime().getTimeInMillis());
  }

  public void updateAccessForum(String forumId) throws Exception {
    String userId = getUserProfile().getUserId();
    if (userId != null && userId.length() > 0) {
      forumService.updateForumAccess(userId, forumId);
    }
    userProfile.setLastTimeAccessForum(forumId, CommonUtils.getGreenwichMeanTime().getTimeInMillis());
  }

  public void removeCacheUserProfile() {
    try {
      forumService.removeCacheUserProfile(getUserProfile().getUserId());
    } catch (Exception e) {
      log.debug("Failed to remove cache userprofile with user: " + userProfile.getUserId());
    }
  }

  public String getPortletLink(String actionName, String userName) {
    try {
      return event(actionName, userName);
    } catch (Exception e) {
      log.debug("Failed to set link to view info user.", e);
      return null;
    }
  }

  protected String getCometdContextName() {
    EXoContinuationBayeux bayeux = (EXoContinuationBayeux) PortalContainer.getInstance()
                                                                          .getComponentInstanceOfType(EXoContinuationBayeux.class);
    return (bayeux == null ? "cometd" : bayeux.getCometdContextName());
  }

  public String getUserToken() {
    try {
      ContinuationService continuation = (ContinuationService) PortalContainer.getInstance()
                                                                         .getComponentInstanceOfType(ContinuationService.class);
      return continuation.getUserToken(getUserProfile().getUserId());
    } catch (Exception e) {
      log.error("Could not retrieve continuation token for user " + userProfile.getUserId(), e);
    }
    return ForumUtils.EMPTY_STR;
  }

  protected void initSendNotification() throws Exception {
    if(getUserProfile().getUserRole() <=2 ) {
      String postLink = ForumUtils.createdSubForumLink(Utils.TOPIC, "topicID", false);
      StringBuilder init = new StringBuilder("forumNotify.initCometd('");
      init.append(userProfile.getUserId()).append("', '")
          .append(getUserToken()).append("', '")
          .append(getCometdContextName()).append("');");

      StringBuilder initParam = new StringBuilder();
      initParam.append("forumNotify.initParam(\"").append(getId()).append("\", \"").append(postLink).append("\", ")
               .append("{")
               .append("notification : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "Notification")).append("\", ")
               .append("privatePost : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "NewPrivatePost")).append("\", ")
               .append("privateMessage : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "NewPrivateMessage")).append("\", ")
               .append("from : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "from")).append("\", ")
               .append("briefContent : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "briefContent")).append("\", ")
               .append("goDirectlyToPost : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "GoDirectlyToPost")).append("\", ")
               .append("goDirectlyToMessage : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "GoDirectlyToMessage")).append("\", ")
               .append("clickHere : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "ClickHere")).append("\", ")
               .append("title : \"").append(WebUIUtils.getLabelEscapedJavaScript(getId(), "Title")).append("\"")
               .append("});");
      ForumUtils.addScripts("ForumSendNotification", "forumNotify", new String[] { initParam.toString(), init.toString() })
                .require("SHARED/jquery_cometd", "cometd");
    }
  }

  public boolean checkForumHasAddTopic(String categoryId, String forumId) throws Exception {
    // is guest or banned 
    if (getUserProfile().getUserRole() == UserProfile.GUEST || 
        getUserProfile().getIsBanned() || getUserProfile().isDisabled()) {
      return false;
    }
    try {
      Category cate = forumService.getCategory(categoryId);
      Forum forum = forumService.getForum(categoryId, forumId);
      if (forum == null) {
        return false;
      }
      // forum close or lock
      if (forum.getIsClosed() || forum.getIsLock()) {
        return false;
      }
      // isAdmin
      if (getUserProfile().getUserRole() == 0) {
        return true;
      }
      // is moderator
      if (getUserProfile().getUserRole() == 1) {
        String[] morderators = ForumUtils.arraysMerge(cate.getModerators(), forum.getModerators());
        //
        if (ForumServiceUtils.isModerator(morderators, userProfile.getUserId())) {
          return true;
        }
      }
      // ban IP of forum.
      if (isEnableIPLogging() && forum.getBanIP() != null && forum.getBanIP().contains(WebUIUtils.getRemoteIP())) {
        return false;
      }
      // check access category
      if (!ForumServiceUtils.hasPermission(cate.getUserPrivate(), userProfile.getUserId())) {
        return false;
      }
      // can add topic on category/forum
      String[] canCreadTopic = ForumUtils.arraysMerge(forum.getCreateTopicRole(), cate.getCreateTopicRole());
      if (!ForumServiceUtils.hasPermission(canCreadTopic, userProfile.getUserId())) {
        return false;
      }
    } catch (Exception e) {
      log.warn(String.format("Check permission to add topic of category %s, forum %s unsuccessfully.", categoryId, forumId));
      log.debug(e);
      return false;
    }
    return true;
  }
  
  public boolean checkForumHasAddPost(String categoryId, String forumId, String topicId) throws Exception {
    // is guest or banned 
    if (getUserProfile().getUserRole() == UserProfile.GUEST || 
        getUserProfile().getIsBanned() || getUserProfile().isDisabled()) {
      return false;
    }
    try {
      Category cate = forumService.getCategory(categoryId);
      Forum forum = forumService.getForum(categoryId, forumId);
      Topic topic = forumService.getTopic(categoryId, forumId, topicId, null);
      String userId = getUserProfile().getUserId();
      //
      if (topic == null) {
        return false;
      }
      // topic is closed/locked
      if (topic.getIsClosed() || topic.getIsLock() || forum.getIsClosed() || forum.getIsLock()) {
        return false;
      }
      // isAdmin
      if (getUserProfile().getUserRole() == 0) return true;
      // is moderator
      if (getUserProfile().getUserRole() == 1) {
        String[] morderators = ForumUtils.arraysMerge(cate.getModerators(), forum.getModerators());
        //
        if (ForumServiceUtils.isModerator(morderators, userId)) {
          return true;
        }
      }
      // ban IP of forum.
      if (isEnableIPLogging() && forum.getBanIP() != null && forum.getBanIP().contains(WebUIUtils.getRemoteIP())) {
        return false;
      }
      // check access category
      if (!ForumServiceUtils.hasPermission(cate.getUserPrivate(), userId)) {
        return false;
      }
      //topic not active
      if (!topic.getIsActive() || !topic.getIsActiveByForum() || topic.getIsWaiting() || 
          (forum.getIsModerateTopic() && !topic.getIsApproved())) {
        return false;
      }
      // owner topic
      if (topic.getOwner() != null && topic.getOwner().equals(userId)) {
        return true;
      }
      // check can post
      String[] canCreadPost = ForumUtils.arraysMerge(cate.getPoster(), ForumUtils.arraysMerge(topic.getCanPost(), forum.getPoster()));
      if (!ForumServiceUtils.hasPermission(canCreadPost, userId)) {
        return false;
      }
    } catch (Exception e) {
      log.warn(String.format("Check permission to add post of category %s, forum %s, topic %s unsuccessfully.", categoryId, forumId, topicId));
      log.debug(e);
      return false;
    }
    return true;
  }

  public boolean checkCanView(Category cate, Forum forum, Topic topic) throws Exception {
    // check category scoping
    if(invisibleCategories != null && invisibleCategories.isEmpty() == false 
        && invisibleCategories.contains(cate.getId()) == false) {
      return false;
    }
    // check forum scoping
    if(forum != null && invisibleForums != null && invisibleForums.isEmpty() == false 
        && invisibleForums.contains(forum.getId()) == false) {
      return false;
    }
    // isAdmin
    if (getUserProfile().getUserRole() == 0) {
      return true;
    }

    String userId = getUserProfile().getUserId();
    // is moderator
    if (getUserProfile().getUserRole() == 1) {
      String[] morderators = ForumUtils.arraysMerge(cate.getModerators(), (forum != null) ? forum.getModerators() : new String[] {});
      //
      if (ForumServiceUtils.isModerator(morderators, userId)) {
        return true;
      }
    }
    // check access category
    if (!ForumServiceUtils.hasPermission(cate.getUserPrivate(), userId)) {
      return false;
    }
    if (topic != null) {
      // owner topic
      if (topic.getOwner() != null && topic.getOwner().equals(userId)) {
        return true;
      }
      // topic not active
      if (!topic.getIsActive() || !topic.getIsActiveByForum() || topic.getIsWaiting() || (forum.getIsModerateTopic() && !topic.getIsApproved())) {
        return false;
      }
      // check can view
      String[] canView = ForumUtils.arraysMerge(cate.getViewer(), ForumUtils.arraysMerge(topic.getCanView(), forum.getViewer()));
      if (!ForumServiceUtils.hasPermission(canView, userId)) {
        return false;
      }
    }
    return true;
  }
  
  public static void showWarningMessage(WebuiRequestContext context, String key, String... args) {
    context.getUIApplication().addMessage(new ApplicationMessage(key, args, ApplicationMessage.WARNING));
  }
  
  public void calculateRenderComponent(String path, WebuiRequestContext context) throws Exception {
    ResourceBundle res = context.getApplicationResourceBundle();
    //
    String openType = Utils.getObjectType(path);
    if (Utils.FORUM_SERVICE.equals(openType)) {
      renderForumHome();
    } else if (ForumUtils.FIELD_SEARCHFORUM_LABEL.equals(openType)) {
      updateIsRendered(ForumUtils.FIELD_SEARCHFORUM_LABEL);
      UISearchForm searchForm = getChild(UISearchForm.class);
      searchForm.setPath(ForumUtils.EMPTY_STR);
      searchForm.setSelectType(path.replaceFirst(ForumUtils.FIELD_SEARCHFORUM_LABEL, ""));
      searchForm.setSearchOptionsObjectType(ForumUtils.EMPTY_STR);
      path = ForumUtils.FIELD_EXOFORUM_LABEL;
    } else if (openType.indexOf(Utils.TAG) == 0) {
      updateIsRendered(ForumUtils.TAG);
      getChild(UITopicsTag.class).setIdTag(path);
    } else if (Utils.TOPIC.equals(openType) || Utils.POST.equals(openType)) {
      boolean isReply = false, isQuote = false;
      if (path.indexOf("/true") > 0) {
        isQuote = true;
        path = path.replaceFirst("/true", ForumUtils.EMPTY_STR);
      } else if (path.indexOf("/false") > 0) {
        isReply = true;
        path = path.replaceFirst("/false", ForumUtils.EMPTY_STR);
      }
      if(path.indexOf(Utils.CATEGORY) > 0) {
        path = path.substring(path.indexOf(Utils.CATEGORY));
      }
      String[] id = path.split(ForumUtils.SLASH);
      String postId = "top";
      int page = 0;
      if (path.indexOf(ForumUtils.VIEW_LAST_POST) > 0) {
        postId = ForumUtils.VIEW_LAST_POST;
        path = path.replace(ForumUtils.SLASH + ForumUtils.VIEW_LAST_POST, ForumUtils.EMPTY_STR);
        id = path.split(ForumUtils.SLASH);
      } else if(path.indexOf(Utils.POST) > 0) {
        postId = id[id.length - 1];
        path = path.substring(0, path.lastIndexOf(ForumUtils.SLASH));
        id = path.split(ForumUtils.SLASH);
      } else if (id.length > 1) {
        try {
          page = Integer.parseInt(id[id.length - 1]);
        } catch (NumberFormatException e) {
          if (log.isDebugEnabled()){
            log.debug("Failed to parse number " + id[id.length - 1], e);
          }
        }
        if (page > 0) {
          path = path.replace(ForumUtils.SLASH + id[id.length - 1], ForumUtils.EMPTY_STR);
          id = path.split(ForumUtils.SLASH);
        } else
          page = 0;
      }
      try {
        Topic topic;
        if (id.length > 1) {
          topic = this.forumService.getTopicByPath(path, false);
        } else {
          topic = (Topic) this.forumService.getObjectNameById(path, Utils.TOPIC);
        }
        if (topic != null) {
          if (path.indexOf(ForumUtils.SLASH) < 0) {
            path = topic.getPath();
            path = path.substring(path.indexOf(Utils.CATEGORY));
            id = path.split(ForumUtils.SLASH);
          }
          Category category = this.forumService.getCategory(id[0]);
          Forum forum = this.forumService.getForum(id[0], id[1]);
          if (this.checkCanView(category, forum, topic)) {
            this.updateIsRendered(ForumUtils.FORUM);
            UIForumContainer uiForumContainer = this.getChild(UIForumContainer.class);
            UITopicDetailContainer uiTopicDetailContainer = uiForumContainer.getChild(UITopicDetailContainer.class);
            uiForumContainer.setIsRenderChild(false);
            uiForumContainer.getChild(UIForumDescription.class).setForum(forum);
            UITopicDetail uiTopicDetail = uiTopicDetailContainer.getChild(UITopicDetail.class);
            uiTopicDetail.setIsEditTopic(true);
            uiTopicDetail.setUpdateForum(forum);
            uiTopicDetail.initInfoTopic(id[0], id[1], topic, page);
            uiTopicDetailContainer.getChild(UITopicPoll.class).updateFormPoll(id[0], id[1], topic.getId());
            uiTopicDetail.setIdPostView(postId);
            uiTopicDetail.setLastPostId(((path.indexOf(Utils.POST) < 0) ? ForumUtils.EMPTY_STR : postId));
            if (isReply || isQuote) {
              if (uiTopicDetail.getCanPost()) {
                try {
                  UIPopupAction popupAction = this.getChild(UIPopupAction.class);
                  UIPopupContainer popupContainer = popupAction.createUIComponent(UIPopupContainer.class, null, null);
                  UIPostForm postForm = popupContainer.addChild(UIPostForm.class, null, null);
                  boolean isMod = ForumServiceUtils.isModerator(forum.getModerators(), this.userProfile.getUserId());
                  postForm.setPostIds(id[0], id[1], topic.getId(), topic);
                  postForm.setMod(isMod);
                  Post post = this.forumService.getPost(id[0], id[1], topic.getId(), postId);
                  if (isQuote) {
                    if (post != null) {
                      postForm.updatePost(postId, true, (post.getUserPrivate().length > 1), post);
                      popupContainer.setId("UIQuoteContainer");
                    } else {
                      showWarningMessage(context, "UIBreadcumbs.msg.post-no-longer-exist", ForumUtils.EMPTY_STR);
                      uiTopicDetail.setIdPostView("normal");
                    }
                  } else {
                    if (post != null && post.getUserPrivate().length > 1) {
                      postForm.updatePost(post.getId(), false, true, post);
                    } else {
                      postForm.updatePost(ForumUtils.EMPTY_STR, false, false, null);
                    }
                    popupContainer.setId("UIAddPostContainer");
                  }
                  popupAction.activate(popupContainer, 900, 500);
                } catch (Exception e) {
                  log.error(e);
                }
              } else {
                showWarningMessage(context, "UIPostForm.msg.no-permission", ForumUtils.EMPTY_STR);
              }
              String fullUrl = ((HttpServletRequest) Util.getPortalRequestContext().getRequest()).getRequestURL().toString();
              context.getJavascriptManager().getRequireJS().addScripts(ForumUtils.replaceStateURL(fullUrl));
            }
            if (!UserHelper.isAnonim()) {
              this.forumService.updateTopicAccess(userProfile.getUserId(), topic.getId());
              this.getUserProfile().setLastTimeAccessTopic(topic.getId(), CommonUtils.getGreenwichMeanTime().getTimeInMillis());
            }
          } else {
            showWarningMessage(context, "UIForumPortlet.msg.do-not-permission-view-topic", 
                               new String[] {});
            if (!ForumUtils.isEmpty(getForumIdOfSpace())) {
              calculateRenderComponent(forumSpId, context);
            } else {
              renderForumHome();
              path = Utils.FORUM_SERVICE;
            }
          }
        } else if (ForumUtils.isEmpty(getForumIdOfSpace()) == false) {// open topic removed on forum of space
          if (forumService.getForum(categorySpId, forumSpId) == null) {// the forum of space had been removed
            forumSpDeleted = true;
            removeAllChildPorletView();
            log.info("The forum in space " + spaceDisplayName + " no longer exists.");
            return;
          } else {
            showWarningMessage(context, "UIForumPortlet.msg.topicEmpty", ForumUtils.EMPTY_STR);
            calculateRenderComponent(forumSpId, context);
          }
        } else {// open topic removed on normal forum.
          showWarningMessage(context, "UIForumPortlet.msg.topicEmpty", ForumUtils.EMPTY_STR);
          renderForumHome();
          path = Utils.FORUM_SERVICE;
        }
      } catch (Exception e) {// Unknown error
        if (log.isDebugEnabled()){
          log.debug("Failed to render forum link: [" + path + "]. Forum home will be rendered.\nCaused by:", e);
        }
        showWarningMessage(context, "UIShowBookMarkForm.msg.link-not-found", ForumUtils.EMPTY_STR);
        renderForumHome();
        path = Utils.FORUM_SERVICE;
      }
    } else if (Utils.FORUM.equals(openType)) {
      try {
        Forum forum = null;
        String cateId = null;
        int page = 0;
        if (path.indexOf(ForumUtils.SLASH) >= 0) {
          String[] arr = path.split(ForumUtils.SLASH);
          try {
            page = Integer.parseInt(arr[arr.length - 1]);
          } catch (Exception e) {
            if (log.isDebugEnabled()){
              log.debug("Failed to parse number " + arr[arr.length - 1], e);
            }
          }
          if (arr[0].indexOf(Utils.CATEGORY) == 0) {
            cateId = arr[0];
            forum = this.forumService.getForum(cateId, arr[1]);
          } else {
            forum = (Forum) this.forumService.getObjectNameById(arr[0], Utils.FORUM);
          }
        }
        if (forum == null) {
          path = path.substring(path.lastIndexOf(ForumUtils.SLASH) + 1);
          forum = (Forum) this.forumService.getObjectNameById(path, Utils.FORUM);
          if (forum == null && path.equals(getForumIdOfSpace())) {
            forum = forumService.getForum(this.categorySpId, path);
            if(forum == null) {
              forumSpDeleted = true;
              removeAllChildPorletView();
              log.info("The forum in space " + spaceDisplayName + " no longer exists.");
              return;
            }
          }
        }
        if (cateId == null) {
          cateId = forum.getCategoryId();
        }
        path = new StringBuilder(cateId).append("/").append(forum.getId()).toString();
        Category category = this.forumService.getCategory(cateId);
        if (this.checkCanView(category, forum, null)) {
          this.updateIsRendered(ForumUtils.FORUM);
          UIForumContainer forumContainer = this.findFirstComponentOfType(UIForumContainer.class);
          forumContainer.setIsRenderChild(true);
          forumContainer.getChild(UIForumDescription.class).setForum(forum);
          UITopicContainer topicContainer = forumContainer.getChild(UITopicContainer.class);
          topicContainer.setUpdateForum(cateId, forum, page);

          if(!userProfile.getUserId().equals(UserProfile.USER_GUEST)) {
            //
            PortalRequestContext portalContext = Util.getPortalRequestContext();
            String hasCreateTopic = portalContext.getRequestParameter(ForumUtils.HAS_CREATE_TOPIC);
            if(!ForumUtils.isEmpty(hasCreateTopic) && Boolean.parseBoolean(hasCreateTopic)) {
              Event<UIComponent> addTopicEvent = topicContainer.createEvent("AddTopic", Event.Phase.PROCESS, context);
              if (addTopicEvent != null) {
                addTopicEvent.broadcast();
              }
            } else {
              String hasCreatePoll = portalContext.getRequestParameter(ForumUtils.HAS_CREATE_POLL);
              if(!ForumUtils.isEmpty(hasCreatePoll) && Boolean.parseBoolean(hasCreatePoll)) {
                Event<UIComponent> addTopicEvent = topicContainer.createEvent("AddPoll", Event.Phase.PROCESS, context);
                if (addTopicEvent != null) {
                  addTopicEvent.broadcast();
                }
              }
            }
            
          }
         
        } else {
          showWarningMessage(context, "UIForumPortlet.msg.do-not-permission-view-forum", 
                             new String[] {});
          renderForumHome();
          path = Utils.FORUM_SERVICE;
        }
      } catch (Exception e) {
        if (log.isDebugEnabled()){
          log.debug("Failed to render forum link: [" + path + "]. Forum home will be rendered.\nCaused by:", e);
        }
        showWarningMessage(context, "UIShowBookMarkForm.msg.link-not-found", 
                           new String[] { res.getString("UIForumPortlet.label.forum") });
        renderForumHome();
        path = Utils.FORUM_SERVICE;
      }
    } else if (Utils.CATEGORY.equals(openType)) {
      UICategoryContainer categoryContainer = this.getChild(UICategoryContainer.class);
      try {
        Category category = this.forumService.getCategory(path);
        if (this.checkCanView(category, null, null)) {
          categoryContainer.getChild(UICategory.class).updateByLink(category);
          categoryContainer.updateIsRender(false);
          this.updateIsRendered(ForumUtils.CATEGORIES);
        } else {
          showWarningMessage(context, "UIForumPortlet.msg.do-not-permission-view-category", 
                             new String[] {});
          renderForumHome();
          path = Utils.FORUM_SERVICE;
        }
      } catch (Exception e) {
        if (log.isDebugEnabled()){
          log.debug("Failed to render forum link: [" + path + "]. Forum home will be rendered.\nCaused by:", e);
        }
        showWarningMessage(context, "UIShowBookMarkForm.msg.link-not-found", ForumUtils.EMPTY_STR);
        renderForumHome();
        path = Utils.FORUM_SERVICE;
      }
    } else {
      if (log.isDebugEnabled()){
        log.debug("Failed to render forum link: [" + path + "]. Forum home will be rendered.");
      }
      renderForumHome();
      path = Utils.FORUM_SERVICE;
    }
    getChild(UIBreadcumbs.class).setUpdataPath(path);
  }

  
  static public class OpenLinkActionListener extends EventListener<UIForumPortlet> {
    public void execute(Event<UIForumPortlet> event) throws Exception {
      UIForumPortlet forumPortlet = event.getSource();
      String path = event.getRequestContext().getRequestParameter(OBJECTID);
      if (ForumUtils.isEmpty(path))
        return;
      forumPortlet.calculateRenderComponent(path, event.getRequestContext());
      event.getRequestContext().addUIComponentToUpdateByAjax(forumPortlet);
    }
  }

  static public class ViewPublicUserInfoActionListener extends EventListener<UIForumPortlet> {
    public void execute(Event<UIForumPortlet> event) throws Exception {
      UIForumPortlet forumPortlet = event.getSource();
      String userId = event.getRequestContext().getRequestParameter(OBJECTID).trim();
      UIPopupAction popupAction = forumPortlet.getChild(UIPopupAction.class);
      UIViewUserProfile viewUserProfile = popupAction.createUIComponent(UIViewUserProfile.class, null, null);
      UserProfile selectProfile;
      try {
        selectProfile = forumPortlet.forumService.getUserInformations(forumPortlet.forumService.getQuickProfile(userId));
      } catch (Exception e) {
        selectProfile = ForumUtils.getDeletedUserProfile(forumPortlet.forumService, userId);
      }
      viewUserProfile.setUserProfileViewer(selectProfile);
      popupAction.activate(viewUserProfile, 670, 400, true);
      event.getRequestContext().addUIComponentToUpdateByAjax(popupAction);
    }
  }

  static public class PrivateMessageActionListener extends EventListener<UIForumPortlet> {
    public void execute(Event<UIForumPortlet> event) throws Exception {
      UIForumPortlet forumPortlet = event.getSource();
      if (forumPortlet.userProfile.getIsBanned()) {
        showWarningMessage(event.getRequestContext(), "UITopicDetail.msg.userIsBannedCanNotSendMail", ForumUtils.EMPTY_STR);
        return;
      }
      String userId = event.getRequestContext().getRequestParameter(OBJECTID);
      if (userId.indexOf(Utils.DELETED) > 0 && ForumServiceUtils.isDisableUser(userId.trim())) {
        showWarningMessage(event.getRequestContext(), "UITopicDetail.msg.userIsDeleted", userId.replace(Utils.DELETED, ""));
        return;
      }
      UIPopupAction popupAction = forumPortlet.getChild(UIPopupAction.class);
      UIPopupContainer popupContainer = popupAction.createUIComponent(UIPopupContainer.class, null, null);
      UIPrivateMessageForm messageForm = popupContainer.addChild(UIPrivateMessageForm.class, null, null);
      messageForm.setFullMessage(false);
      messageForm.setUserProfile(forumPortlet.userProfile);
      messageForm.setSendtoField(userId);
      popupContainer.setId("PrivateMessageForm");
      popupAction.activate(popupContainer, 720, 550);
      event.getRequestContext().addUIComponentToUpdateByAjax(popupAction);
    }
  }

  static public class ViewPostedByUserActionListener extends EventListener<UIForumPortlet> {
    public void execute(Event<UIForumPortlet> event) throws Exception {
      String userId = event.getRequestContext().getRequestParameter(OBJECTID);
      UIForumPortlet forumPortlet = event.getSource();
      UIPopupAction popupAction = forumPortlet.getChild(UIPopupAction.class);
      UIPopupContainer popupContainer = popupAction.createUIComponent(UIPopupContainer.class, null, null);
      UIViewPostedByUser viewPostedByUser = popupContainer.addChild(UIViewPostedByUser.class, null, null);
      viewPostedByUser.setUserProfile(userId);
      popupContainer.setId("ViewPostedByUser");
      popupAction.activate(popupContainer, 760, 370);
      event.getRequestContext().addUIComponentToUpdateByAjax(popupAction);
    }
  }

  static public class ViewThreadByUserActionListener extends EventListener<UIForumPortlet> {
    public void execute(Event<UIForumPortlet> event) throws Exception {
      String userId = event.getRequestContext().getRequestParameter(OBJECTID);
      UIForumPortlet forumPortlet = event.getSource();
      UIPopupAction popupAction = forumPortlet.getChild(UIPopupAction.class);
      UIPopupContainer popupContainer = popupAction.createUIComponent(UIPopupContainer.class, null, null);
      UIViewTopicCreatedByUser topicCreatedByUser = popupContainer.addChild(UIViewTopicCreatedByUser.class, null, null);
      topicCreatedByUser.setUserId(userId);
      popupContainer.setId("ViewTopicCreatedByUser");
      popupAction.activate(popupContainer, 760, 450);
      event.getRequestContext().addUIComponentToUpdateByAjax(popupAction);
    }
  }
}
