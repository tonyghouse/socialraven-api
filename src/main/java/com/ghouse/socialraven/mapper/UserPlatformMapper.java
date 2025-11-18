package com.ghouse.socialraven.mapper;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserPlatformMapper {


//
//	@Mapping(target = "sectionId", source = "section.sectionId")
//	@Mapping(target = "sectionTitleDesc", source = "section.sectionName")
//	@Mapping(target = "sectionTitle", source = "section.sectionName",qualifiedByName = "convertToTitle")
//	@BeanMapping(ignoreByDefault = false)
//	SectionDetails toSectionDetails(Section section);
//
//	@Named("convertToTitle")
//	public static String convertToTitle(String sectionName) {
//		if(sectionName == null){
//			return null;
//		}
//		if(sectionName.length() > TITLE_CHARACTER_LIMIT){
//			return sectionName.substring(0, TITLE_CHARACTER_LIMIT)+"...";
//		}
//
//		return sectionName;
//
//	}
}