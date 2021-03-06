package cft.commons.showcase.web.interceptor;

import java.text.SimpleDateFormat;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StopWatch;

import cft.commons.core.constant.Constants;
import cft.commons.core.helper.jackson.JsonMapper;
import cft.commons.showcase.constant.CommonConstants;
import cft.commons.showcase.dao.RedisDAO;
import cft.commons.showcase.model.ActionLog;

/**
 * @author Daniel
 */
@Slf4j
public class ActionLogInterceptor implements MethodInterceptor {

	@Autowired
	private RedisDAO redisDAO;

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {

		Object result = null;

		String classNameWithPackage = invocation.getThis().getClass().getName();
		String className = StringUtils.substringAfterLast(classNameWithPackage, ".");
		String methodName = invocation.getMethod().getName();
		Object[] arguments = invocation.getArguments();

		StringBuffer logBody = new StringBuffer();

		logBody.append(methodName);

		if (log.isDebugEnabled()) {
			log.debug(logBody + " ------> Start");
		}

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		result = invocation.proceed();
		stopWatch.stop();
		long executeTime = stopWatch.getTotalTimeMillis();

		ActionLog systemLog = new ActionLog();
		systemLog.setLogTime(new Date());
		systemLog.setLogType("INFO");
		systemLog.setLogClass(className);
		systemLog.setLogBody("Invoked method:  " + methodName);
		systemLog.setExecuteTime(executeTime);

		JsonMapper jsonMapper = new JsonMapper();
		jsonMapper.getMapper().setDateFormat(new SimpleDateFormat(Constants.C_DATETIME_PATTERN_DEFAULT));
		String logString = jsonMapper.toJson(systemLog);

		try {
			redisDAO.leftPushQueue(CommonConstants.KEY_SYSTEM_LOG_ACTION, logString, 1000);
		} catch (Exception ex) {
			log.error("Exception during redisDAO.leftPushQueue.", ex);
		}

		//Generate Function end log
		logBody.append(" ------> End");
		logBody.append(" ,execution time: ");
		logBody.append(executeTime);
		logBody.append(" ms");

		log.debug(logBody.toString());

		return result;
	}
}