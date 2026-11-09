package org.openkatmai.flighttrace;
import org.json.*;
final class CautionText {
 static String describe(JSONObject c,boolean fresh){
  if(!fresh)return "CAUTIONS: stream unavailable\nCurrent aircraft warnings cannot be confirmed";
  StringBuilder out=new StringBuilder("AIRCRAFT STATUS / CAUTIONS\n");
  JSONObject power=c.optJSONObject("power_mode");String mode=power==null?"":power.optString("currentState","");
  if("LOW".equals(mode))out.append("ECO MODE - aircraft low-power mode\n");
  else if("NORMAL_TO_LOW".equals(mode))out.append("Entering ECO mode\n");
  else if("LOW_TO_NORMAL".equals(mode))out.append("Leaving ECO mode\n");
  else out.append("Power mode: ").append(mode.isEmpty()?"unavailable":mode).append('\n');
  if(power!=null&&power.has("changedReason"))out.append("Power-mode reason: ").append(power.opt("changedReason")).append('\n');
  Object hms=c.opt("hms_diagnostics");
  if(hms instanceof JSONArray){JSONArray list=(JSONArray)hms;out.append("Received HMS entries: ").append(list.length()).append('\n');for(int i=0;i<Math.min(30,list.length());i++)out.append(list.opt(i)).append('\n');}
  else if(hms!=null&&hms!=JSONObject.NULL)out.append("HMS: ").append(hms).append('\n');
  else out.append("HMS diagnostic data unavailable\n");
  if(Boolean.TRUE.equals(c.opt("motor_overload")))out.append("Motor overload reported in cache (may be historical)\n");
  Object wind=c.opt("wind_warning_level");if(wind!=null&&wind!=JSONObject.NULL)out.append("Wind warning level: ").append(wind).append('\n');
  return out.append("Partial cache coverage; continue checking DJI Fly warnings").toString();
 }
}
