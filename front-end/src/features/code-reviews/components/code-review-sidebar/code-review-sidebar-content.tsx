import { BsPerson } from "react-icons/bs";
import { useEffect } from "react";
import { useRouter } from "next/router";

import { useCodeReviewsForCodeUpdate } from "../../api/get-code-reviews-for-code-update";
import { useCodeReviewsForCodeUpdateSelection } from "../../stores/code-review-for-code-update-selection";
import {
  ItemContainer,
  Item,
  ItemUserName,
  ItemContent,
} from "./code-review-sidebar-content-style";

type CodeReviewSidebarContentProps = {
  codeId: string;
};

export const CodeReviewSidebarContent = ({
  codeId,
}: CodeReviewSidebarContentProps) => {
  // server states
  const codeReviewsForCodeUpdateQuery = useCodeReviewsForCodeUpdate({ codeId });

  // client states
  const {
    selectedCodeReviewId,
    toggleCodeReviewForCodeUpdateSelection,
    resetCodeReviewForCodeUpdateSelection,
  } = useCodeReviewsForCodeUpdateSelection();

  // reset project feedback selection state on route change
  const dynamicRoute = useRouter().asPath;
  useEffect(
    () => resetCodeReviewForCodeUpdateSelection(),
    [dynamicRoute, resetCodeReviewForCodeUpdateSelection]
  );

  // fill initial selection status on each project feedback
  useEffect(() => {
    codeReviewsForCodeUpdateQuery.data?.forEach((codeReview) => {
      toggleCodeReviewForCodeUpdateSelection(codeReview.reviewId);
    });
  }, [
    codeReviewsForCodeUpdateQuery.data,
    toggleCodeReviewForCodeUpdateSelection,
  ]);

  // isLoading
  if (codeReviewsForCodeUpdateQuery.isLoading) {
    return <>Loading...</>;
  }

  // isError
  if (codeReviewsForCodeUpdateQuery.isError) {
    return <>Error!</>;
  }

  // server data & click function definition
  const codeReviewsForCodeUpdate = codeReviewsForCodeUpdateQuery.data;
  const handleClick = (codeReviewId: number) => {
    toggleCodeReviewForCodeUpdateSelection(codeReviewId);
  };

  return (
    <ItemContainer>
      {codeReviewsForCodeUpdate.map((codeReview, index) => (
        <Item
          key={`code-review-for-code-update-${codeId}-${index}`}
          onClick={() => handleClick(codeReview.reviewId)}
          isSelected={selectedCodeReviewId.includes(codeReview.reviewId)}
        >
          <ItemUserName>
            <BsPerson color="var(--font-color)" />
            {codeReview.userName}
          </ItemUserName>
          <ItemContent>{codeReview.content}</ItemContent>
        </Item>
      ))}
    </ItemContainer>
  );
};