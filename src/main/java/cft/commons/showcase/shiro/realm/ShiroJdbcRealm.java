package cft.commons.showcase.shiro.realm;

import java.io.Serializable;

import javax.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;

import cft.commons.core.constant.Constants;
import cft.commons.core.helper.encrypt.EncryptUtils;
import cft.commons.core.util.EncodeUtils;
import cft.commons.showcase.model.Role;
import cft.commons.showcase.model.User;
import cft.commons.showcase.service.UserService;

import com.google.common.base.Objects;

/**
 * @author daniel
 *
 */
@Slf4j
public class ShiroJdbcRealm extends AuthorizingRealm {

	protected UserService userService;

	/**
	 * 认证回调函数,登录时调用.
	 */
	@Override
	protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authcToken) throws AuthenticationException {

		UsernamePasswordToken token = (UsernamePasswordToken) authcToken;
		
		User user = userService.getUserByLoginName(token.getUsername());
		
		if (user != null) {

			if (StringUtils.equals(Constants.STATUS_CODE_INACTIVE, user.getStatus())) {
				throw new DisabledAccountException();
			}

			byte[] salt = EncodeUtils.decodeHex(user.getSalt());
			ShiroUser shiroUser = new ShiroUser(user.getLoginName(), user.getName());

			/*Parameters:
			 * principal - the 'primary' principal associated with the specified realm.
			 * hashedCredentials - the hashed credentials that verify the given principal.
			 * credentialsSalt - the salt used when hashing the given hashedCredentials
			 *realmName - the realm from where the principal and credentials were acquired.
			 */
			return new SimpleAuthenticationInfo(shiroUser, user.getPassword(), ByteSource.Util.bytes(salt), getName());

		} else {
			log.info(Constants.SVC_LOG + "doGetAuthenticationInfo:user is null");
			return null;
		}
	}

	/**
	 * 授权查询回调函数, 进行鉴权在缓存中无用户的授权信息时调用.
	 */
	@Override
	protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {

		SimpleAuthorizationInfo info = null;
		
		ShiroUser shiroUser = (ShiroUser) principals.getPrimaryPrincipal();
		User user = userService.getUserByLoginName(shiroUser.loginName);

		//判断当前用户状态是否正常，如正常则进行用户权限查询，否则直接logout当前用户
		if (user != null && StringUtils.equals(user.getStatus(), Constants.STATUS_CODE_ACTIVE)) {
			log.info(Constants.SVC_LOG + "ShiroDbRealm:doGetAuthorizationInfo:user:" + user);
			info = new SimpleAuthorizationInfo();
			for (Role role : user.getRoleList()) {
				//基于Role的权限信息
				info.addRole(role.getRoleId().toString());
				//基于Permission的权限信息
				info.addStringPermissions(role.retrieveAllPermissions());
			}

			log.info(Constants.SVC_LOG + "AuthorizationInfo:Roles: " + info.getRoles());
			log.info(Constants.SVC_LOG + "AuthorizationInfo:StringPermissions: " + info.getStringPermissions());
		} else {
			log.info(Constants.SVC_LOG + "ShiroDbRealm:doGetAuthorizationInfo:user is null");
			SecurityUtils.getSubject().logout(); //如当前用户已不存在或非活动状态，则直接执行logout
		}

		return info;
	}

	/**
	 * 设定Password校验的Hash算法与迭代次数.
	 */
	@PostConstruct
	public void initCredentialsMatcher() {
		HashedCredentialsMatcher matcher = new HashedCredentialsMatcher(EncryptUtils.HASH_ALGORITHM);
		matcher.setHashIterations(EncryptUtils.HASH_INTERATIONS_1024);

		setCredentialsMatcher(matcher);
	}

	/**
	 * 自定义Authentication对象，使得Subject除了携带用户的登录名外还可以携带更多信息.
	 */
	public static class ShiroUser implements Serializable {
		
		private static final long serialVersionUID = 4253946458931545477L;
				
		public String loginName;
		public String name;

		public ShiroUser(String loginName, String name) {
			this.loginName = loginName;
			this.name = name;
		}

		public String getName() {
			return name;
		}

		/**
		 * 本函数输出将作为默认的<shiro:principal/>输出.
		 */
		@Override
		public String toString() {
			return loginName;
		}

		/**
		 * 重载hashCode,只计算loginName;
		 */
		@Override
		public int hashCode() {
			return Objects.hashCode(loginName);
		}

		/**
		 * 重载equals,只计算loginName;
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ShiroUser other = (ShiroUser) obj;
			if (loginName == null) {
				if (other.loginName != null)
					return false;
			} else if (!loginName.equals(other.loginName))
				return false;
			return true;
		}
	}

	public void setUserService(UserService userService) {
		this.userService = userService;
	}
}
