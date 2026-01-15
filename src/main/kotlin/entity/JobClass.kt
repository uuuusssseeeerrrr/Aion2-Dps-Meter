package com.tbread.entity

enum class JobClass(val className: String, val basicSkillCode: Int) {
    WARRIOR("검성", 9952),
    PALADIN("수호성", 16912),
    RANGER("궁성", 60832),
    ROGUE("살성", 33872),
    SORCERER("마도성", 5648), /* 마도 확인 필요함 */
    CLERIC("치유성", 36176),
    SUMMONER("정령성", 19216),
    SHAMAN("호법성", 53136);

    companion object{
        fun convertFromSkill(skillCode:Int):JobClass?{
            return entries.find { it.basicSkillCode == skillCode }
        }
    }
}