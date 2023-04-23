import { useState } from "react";
import { Count } from "../count";
import { FlexDiv, Nickname, Text } from "../elements";
import { FeedbackItemWrapper } from "./style";
import { Selection } from "../selection";

interface FeedbackItemProps {
  feedback: {
    user: string;
    isApplied: boolean;
    content: string;
    isMine: boolean;
    isLiked: boolean;
    likeCnt: number;
    createdAt: string;
  };
}

export const FeedbackItem = ({
  feedback: { user, isApplied, content, isMine, isLiked, likeCnt, createdAt },
}: FeedbackItemProps) => {
  const [newIsLiked, setNewIsLiked] = useState<boolean>(isLiked);
  const [newLikeCnt, setNewLikeCnt] = useState<number>(likeCnt);

  return (
    <FeedbackItemWrapper
      bgColor={isApplied ? "sub-one" : "white"}
      isMine={isMine}
    >
      <FlexDiv width="100%" justify="space-between">
        <FlexDiv>
          <Nickname nickname={user} />
          {isApplied && (
            <Text color="main" bold={true} padding="0 2rem">
              프로젝트에 반영됨
            </Text>
          )}
        </FlexDiv>
        <FlexDiv>
          <Count
            type="like"
            isChecked={newIsLiked}
            cnt={newLikeCnt}
            setCnt={setNewLikeCnt}
          />
          <Selection isMine={isMine} />
        </FlexDiv>
      </FlexDiv>

      <FlexDiv width="100%" justify="space-between">
        <Text>{content}</Text>
        <Text size="0.8rem">{createdAt}</Text>
      </FlexDiv>
    </FeedbackItemWrapper>
  );
};
